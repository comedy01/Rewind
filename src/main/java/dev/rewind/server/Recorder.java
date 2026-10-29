package dev.rewind.server;

import dev.rewind.Rewind;
import dev.rewind.RewindSettings;
import dev.rewind.core.Chunks;
import dev.rewind.core.Frames;
import dev.rewind.core.Marker;
import dev.rewind.core.Places;
import dev.rewind.core.Retention;
import dev.rewind.core.TimelineView;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

public final class Recorder {
    private static final int MAINTAIN_STEP = 100;
    private static final int EXPLOSION_MERGE = 20;
    private static final int SETTLE_RADIUS = 3;
    private static final int SETTLE_TICKS = 10;

    private static volatile Recorder active;

    private final MinecraftServer server;
    private final Thread thread;
    private final BlockLog blocks;
    private final Blob.Packer packer = new Blob.Packer();
    private final ArrayList<Keyframe> frames = new ArrayList<>();
    private final Map<UUID, CompoundTag> lastTag = new HashMap<>();
    private final Map<UUID, Blob> lastBlob = new HashMap<>();
    private final Map<UUID, Integer> born = new HashMap<>();
    private final Map<UUID, Integer> died = new HashMap<>();
    private final List<Marker> markers = new ArrayList<>();
    private final Set<UUID> doomed = new HashSet<>();

    private final Set<UUID> travelling = new HashSet<>();

    private final Map<UUID, ResourceKey<Level>> playerDimensions = new HashMap<>();
    private final List<Entity> toDiscard = new ArrayList<>();
    private int now;
    private boolean suspended;
    private boolean applying;
    private long frameBytes;

    private boolean previewing;
    private int cursor;
    private final Map<UUID, Blob> applied = new HashMap<>();
    private final Map<UUID, Entity> rebuilt = new HashMap<>();
    private final Set<UUID> discarded = new HashSet<>();

    private volatile TimelineView view;
    private volatile int shownCursor = -1;
    private volatile boolean hasHistory;
    private volatile boolean settled;
    private volatile boolean watchSettling;
    private int visibleEntities = -1;
    private int stableTicks;
    private volatile int failedRestores;
    private long captureNanos;
    private long captureMaxNanos;
    private long maintainMaxNanos;
    private int captures;
    private volatile long lastCommitMillis;
    private volatile long lastMoveMillis;

    private final IdentityHashMap<Object, Usage> usage = new IdentityHashMap<>();
    private final List<Blob> unpacked = new ArrayList<>();

    private static final class Usage {
        int refs;
        long bytes;
    }

    private record EntityShot(ResourceKey<Level> dimension, Blob blob, UUID[] riders) {
    }

    private record Keyframe(int tick, Map<UUID, EntityShot> entities, Map<UUID, PlayerState> players, WorldState world) {
    }

    private Recorder(MinecraftServer server) {
        this.server = server;
        this.thread = Thread.currentThread();
        this.blocks = new BlockLog(server);
    }

    public static Recorder current() {
        return active;
    }

    public static Recorder at(Level level) {
        if (!(level instanceof ServerLevel serverLevel)) {
            return null;
        }
        return forServer(serverLevel.getServer());
    }

    public static Recorder forServer(MinecraftServer server) {
        if (server == null || server.isDedicatedServer() || !server.isSameThread() || !server.isRunning()) {
            return null;
        }
        Recorder recorder = active;
        if (recorder == null || recorder.server != server) {
            recorder = new Recorder(server);
            active = recorder;
        }
        return recorder.suspended ? null : recorder;
    }

    public static void entityLoaded(Entity entity) {
        Recorder recorder = active;
        if (recorder == null || !(entity.level() instanceof ServerLevel level) || level.getServer() != recorder.server
                || !recorder.server.isSameThread() || entity instanceof Player) {
            return;
        }
        UUID id = entity.getUUID();
        boolean gone = recorder.doomed.contains(id);
        if (!gone && recorder.previewing && !recorder.applying) {
            Integer bornAt = recorder.born.get(id);
            Integer diedAt = recorder.died.get(id);
            gone = (bornAt != null && bornAt > recorder.cursor) || (diedAt != null && diedAt <= recorder.cursor);
        }
        if (gone) {
            recorder.toDiscard.add(entity);
        }
    }

    public static boolean freezes(ServerLevel level) {
        Recorder recorder = active;
        return recorder != null && recorder.previewing && recorder.server == level.getServer();
    }

    public static void stopped(MinecraftServer server) {
        Recorder recorder = active;
        if (recorder != null && recorder.server == server) {
            active = null;
            recorder.packer.shutdown();
        }
    }

    public MinecraftServer server() {
        return server;
    }

    public void blockChanging(ServerLevel level, BlockPos pos, BlockState current, BlockEntity blockEntity) {
        blocks.blockChanging(level, pos, current, blockEntity);
    }

    public void blockEntityChanged(ServerLevel level, BlockEntity blockEntity) {
        blocks.blockEntityChanged(level, blockEntity);
    }

    public void blockEntityLoaded(ServerLevel level, BlockEntity blockEntity) {
        blocks.blockEntityLoaded(level, blockEntity);
    }

    public void blockEntityRemoved(ServerLevel level, BlockEntity blockEntity) {
        blocks.blockEntityRemoved(level, blockEntity);
    }

    public void entityAdded(Entity entity) {
        if (!travelling.remove(entity.getUUID())) {
            born.putIfAbsent(entity.getUUID(), now + 1);
        }
    }

    public void entityRemoved(Entity entity, Entity.RemovalReason reason) {
        if (entity instanceof Player) {
            return;
        }
        if (reason == Entity.RemovalReason.CHANGED_DIMENSION) {
            travelling.add(entity.getUUID());
        } else if (reason.shouldDestroy()) {
            died.put(entity.getUUID(), now + 1);
        }
    }

    public void mark(Marker.Kind kind, String label) {
        int tick = now + 1;
        if (kind == Marker.Kind.EXPLOSION && !markers.isEmpty()) {
            Marker last = markers.get(markers.size() - 1);
            if (last.kind() == kind && tick - last.tick() < EXPLOSION_MERGE) {
                return;
            }
        }
        markers.add(new Marker(kind, tick, label));
    }

    public void tickEnd() {
        discardDoomed();
        markTravel();
        now++;
        boolean due = frames.isEmpty() || now - frames.get(frames.size() - 1).tick() >= Retention.FRAME_STEP;
        blocks.flush(now, due);
        if (due) {
            long started = System.nanoTime();
            capture();
            long took = System.nanoTime() - started;
            captureNanos += took;
            captureMaxNanos = Math.max(captureMaxNanos, took);
            captures++;
        }
        if (now % MAINTAIN_STEP == 0) {
            long started = System.nanoTime();
            maintain();
            maintainMaxNanos = Math.max(maintainMaxNanos, System.nanoTime() - started);
        }
        hasHistory = frames.size() > 1;
        if (watchSettling) {
            int visible = 0;
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity ignored : level.getAllEntities()) {
                    visible++;
                }
            }
            stableTicks = visible == visibleEntities ? stableTicks + 1 : 0;
            visibleEntities = visible;
            settled = stableTicks >= SETTLE_TICKS && entitiesLoadedAroundPlayers();
        }
        travelling.clear();
    }

    private void markTravel() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ResourceKey<Level> dimension = player.level().dimension();
            ResourceKey<Level> before = playerDimensions.put(player.getUUID(), dimension);
            if (before != null && before != dimension) {
                mark(Marker.Kind.TRAVEL, "Entered " + Places.name(Ids.of(dimension)));
            }
        }
    }

    public void watchSettling() {
        settled = false;
        stableTicks = 0;
        visibleEntities = -1;
        watchSettling = true;
    }

    public void stopWatchingSettling() {
        watchSettling = false;
    }

    private boolean entitiesLoadedAroundPlayers() {
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            ServerLevel level = (ServerLevel) player.level();
            int cx = player.getBlockX() >> 4;
            int cz = player.getBlockZ() >> 4;
            for (int dx = -SETTLE_RADIUS; dx <= SETTLE_RADIUS; dx++) {
                for (int dz = -SETTLE_RADIUS; dz <= SETTLE_RADIUS; dz++) {
                    if (!level.areEntitiesLoaded(Chunks.pack(cx + dx, cz + dz))) {
                        return false;
                    }
                }
            }
        }
        return true;
    }

    public void begin() {
        watchSettling = false;
        if (previewing) {
            publish();
            return;
        }
        blocks.flush(now, true);
        if (frames.isEmpty() || frames.get(frames.size() - 1).tick() != now) {
            capture();
        }
        previewing = true;
        suspended = true;
        cursor = now;
        applied.clear();
        for (Map.Entry<UUID, EntityShot> entry : frames.get(frames.size() - 1).entities().entrySet()) {
            applied.put(entry.getKey(), entry.getValue().blob());
        }
        publish();
    }

    public void previewTo(int target) {
        if (!previewing) {
            return;
        }
        int tick = Frames.atOrBefore(frameTicks(), Math.max(0, Math.min(now, target)));
        if (tick != cursor) {
            long started = System.nanoTime();
            moveTo(tick);
            lastMoveMillis = (System.nanoTime() - started) / 1_000_000;
            Rewind.LOGGER.debug("Rewind moved to tick {} in {} ms", tick, lastMoveMillis);
        }
        shownCursor = cursor;
    }

    public void commit(int target) {
        if (!previewing) {
            return;
        }
        long started = System.nanoTime();
        previewTo(target);
        int tick = cursor;
        applying = true;
        try {
            blocks.settle(tick, now);
            restorePlayers(frameAt(tick), true);
        } finally {
            applying = false;
        }
        discardDoomed();
        blocks.truncateAfter(tick);
        while (!frames.isEmpty() && frames.get(frames.size() - 1).tick() > tick) {
            uncount(frames.remove(frames.size() - 1));
        }
        markers.removeIf(marker -> marker.tick() > tick);

        born.forEach((id, t) -> {
            if (t > tick && !discarded.contains(id) && !died.containsKey(id)) {
                doomed.add(id);
            }
        });
        born.values().removeIf(t -> t > tick);
        died.values().removeIf(t -> t > tick);
        Rewind.LOGGER.info("Rewound {} ticks", now - tick);
        now = tick;
        end();
        lastCommitMillis = (System.nanoTime() - started) / 1_000_000;
    }

    public void cancel() {
        if (!previewing) {
            return;
        }
        previewTo(now);
        end();
    }

    private void end() {
        blocks.endPreview();
        previewing = false;
        suspended = false;
        applied.clear();
        rebuilt.clear();
        discarded.clear();

        playerDimensions.clear();
        view = null;
        shownCursor = -1;
    }

    public TimelineView view() {
        return view;
    }

    public int shownCursor() {
        return shownCursor;
    }

    public boolean hasHistory() {
        return hasHistory;
    }

    public boolean settled() {
        return settled;
    }

    public int failedRestores() {
        return failedRestores;
    }

    public int now() {
        return now;
    }

    public boolean previewing() {
        return previewing;
    }

    public boolean restored(UUID id) {
        Entity entity = rebuilt.get(id);
        return entity != null && !entity.isRemoved();
    }

    public String lifeOf(Entity entity) {
        return "born " + born.get(entity.getUUID()) + ", died " + died.get(entity.getUUID());
    }

    public long averageCaptureMicros() {
        return captures == 0 ? 0 : captureNanos / captures / 1000;
    }

    public long lastMoveMillis() {
        return lastMoveMillis;
    }

    public int changeCount() {
        return blocks.count();
    }

    public long lastCommitMillis() {
        return lastCommitMillis;
    }

    public long historyBytes() {
        return frameBytes + blocks.bytes();
    }

    public int firstTick() {
        return frames.isEmpty() ? now : frames.get(0).tick();
    }

    public String stats() {
        return String.format(Locale.ROOT,
                "%d keyframes, %d block changes, %d markers, born %d, died %d, doomed %d, entity cache %d, "
                        + "keyframe avg %d us max %.1f ms, maintain max %.1f ms",
                frames.size(), blocks.count(), markers.size(), born.size(), died.size(), doomed.size(), lastBlob.size(),
                averageCaptureMicros(), captureMaxNanos / 1e6, maintainMaxNanos / 1e6);
    }

    public long recountedHistoryBytes() {
        settleSizes();
        Set<Blob> waiting = Collections.newSetFromMap(new IdentityHashMap<>());
        waiting.addAll(unpacked);
        IdentityHashMap<Object, Boolean> seen = new IdentityHashMap<>();
        long total = 0;
        for (Keyframe frame : frames) {
            total += 64 + frame.entities().size() * 48L + frame.players().size() * 160L;
            for (EntityShot shot : frame.entities().values()) {
                if (seen.put(shot.blob(), Boolean.TRUE) == null) {
                    total += waiting.contains(shot.blob()) ? usage.get(shot.blob()).bytes : shot.blob().size() + 16;
                }
            }
            for (PlayerState player : frame.players().values()) {
                for (ItemStack[] items : List.of(player.inventory(), player.enderChest())) {
                    if (seen.put(items, Boolean.TRUE) == null) {
                        total += items.length * 96L;
                    }
                }
            }
        }
        return total + blocks.recountedBytes();
    }

    public void resetMaxima() {
        captureMaxNanos = 0;
        maintainMaxNanos = 0;
    }

    private void publish() {
        String[] places = new String[frames.size()];
        for (int i = 0; i < places.length; i++) {
            for (PlayerState player : frames.get(i).players().values()) {
                places[i] = Ids.of(player.dimension());
            }
        }
        view = new TimelineView(frames.get(0).tick(), now, frameTicks(), places, List.copyOf(markers),
                frameBytes + blocks.bytes());
        shownCursor = cursor;
    }

    private int[] frameTicks() {
        int[] ticks = new int[frames.size()];
        for (int i = 0; i < ticks.length; i++) {
            ticks[i] = frames.get(i).tick();
        }
        return ticks;
    }

    private Keyframe frameAt(int tick) {
        for (int i = frames.size() - 1; i >= 0; i--) {
            if (frames.get(i).tick() <= tick) {
                return frames.get(i);
            }
        }
        return frames.get(0);
    }

    private void capture() {
        Keyframe previous = frames.isEmpty() ? null : frames.get(frames.size() - 1);
        Map<UUID, EntityShot> entities = new HashMap<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : level.getAllEntities()) {
                if (entity instanceof Player || entity.isPassenger() || entity.isRemoved()) {
                    continue;
                }
                EntityShot shot = shoot(level, entity);
                if (shot != null) {
                    entities.put(entity.getUUID(), shot);
                }
            }
        }
        Map<UUID, PlayerState> players = new HashMap<>();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            if (player.isAlive()) {
                PlayerState before = previous == null ? null : previous.players().get(player.getUUID());
                players.put(player.getUUID(), PlayerState.capture(player, before));
            } else if (previous != null && previous.players().containsKey(player.getUUID())) {
                players.put(player.getUUID(), previous.players().get(player.getUUID()));
            }
        }
        WorldState world = WorldState.capture(server);
        if (previous != null && world.sameAs(previous.world())) {
            world = previous.world();
        }
        Keyframe frame = new Keyframe(now, entities, players, world);
        frames.add(frame);
        count(frame);
    }

    private EntityShot shoot(ServerLevel level, Entity entity) {
        CompoundTag tag;
        try {
            tag = Nbt.saveEntity(level, entity);
        } catch (RuntimeException e) {
            Rewind.LOGGER.debug("Could not snapshot {}", entity, e);
            return null;
        }
        if (tag == null) {
            return null;
        }
        UUID id = entity.getUUID();
        Blob shot = lastBlob.get(id);
        if (shot == null || !tag.equals(lastTag.get(id))) {
            shot = packer.submit(tag);
            lastBlob.put(id, shot);
            lastTag.put(id, tag);
        }
        List<UUID> riders = new ArrayList<>();
        for (Entity rider : entity.getIndirectPassengers()) {
            if (!(rider instanceof Player)) {
                riders.add(rider.getUUID());
            }
        }
        return new EntityShot(level.dimension(), shot, riders.toArray(new UUID[0]));
    }

    private void discardDoomed() {
        if (toDiscard.isEmpty()) {
            return;
        }
        List<Entity> batch = new ArrayList<>(toDiscard);
        toDiscard.clear();
        boolean wasSuspended = suspended;
        suspended = true;
        for (Entity entity : batch) {
            if (!entity.isRemoved()) {
                entity.discard();
            }
            if (!doomed.remove(entity.getUUID())) {
                discarded.add(entity.getUUID());
            }
        }
        suspended = wasSuspended;
    }

    private void moveTo(int tick) {
        applying = true;
        try {
            moveToUnsafe(tick);
        } finally {
            applying = false;
        }
        discardDoomed();
    }

    private void moveToUnsafe(int tick) {
        Keyframe frame = frameAt(tick);
        blocks.move(cursor, tick, now);
        restoreEntities(frame, tick);
        restorePlayers(frame, false);
        frame.world().restore(server);
        cursor = tick;
    }

    private void restorePlayers(Keyframe frame, boolean changeDimension) {
        for (Map.Entry<UUID, PlayerState> entry : frame.players().entrySet()) {
            ServerPlayer player = server.getPlayerList().getPlayer(entry.getKey());
            if (player != null && player.isAlive()) {
                entry.getValue().restore(server, player, changeDimension);
            }
        }
    }

    private void restoreEntities(Keyframe frame, int tick) {
        Set<UUID> wanted = new HashSet<>();
        for (Map.Entry<UUID, EntityShot> entry : frame.entities().entrySet()) {
            wanted.add(entry.getKey());
            wanted.addAll(List.of(entry.getValue().riders()));
        }
        Set<UUID> kept = new HashSet<>();
        List<Entity> remove = new ArrayList<>();
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : present(level)) {
                if (entity instanceof Player || entity.isPassenger()) {
                    continue;
                }
                UUID id = entity.getUUID();
                EntityShot shot = frame.entities().get(id);
                if (shot != null && applied.get(id) == shot.blob() && shot.dimension() == level.dimension()
                        && !entity.isRemoved()) {
                    kept.add(id);
                    for (Entity rider : entity.getIndirectPassengers()) {
                        kept.add(rider.getUUID());
                    }
                }
            }
        }
        for (ServerLevel level : server.getAllLevels()) {
            for (Entity entity : present(level)) {
                if (entity instanceof Player || kept.contains(entity.getUUID())) {
                    continue;
                }
                UUID id = entity.getUUID();
                Integer bornAt = born.get(id);
                Integer diedAt = died.get(id);

                boolean stray = tick == now && rebuilt.containsKey(id) && !wanted.contains(id);
                if (wanted.contains(id) || stray || (bornAt != null && bornAt > tick) || (diedAt != null && diedAt <= tick)) {
                    remove.add(entity);
                }
            }
        }
        for (Entity entity : remove) {
            entity.ejectPassengers();
            entity.discard();
            applied.remove(entity.getUUID());
            discarded.add(entity.getUUID());
        }
        for (Map.Entry<UUID, EntityShot> entry : frame.entities().entrySet()) {
            UUID id = entry.getKey();
            if (kept.contains(id)) {
                continue;
            }
            EntityShot shot = entry.getValue();
            ServerLevel level = server.getLevel(shot.dimension());
            if (level == null || level.getEntity(id) != null) {
                continue;
            }
            Entity entity = Nbt.loadEntity(level, shot.blob().read());
            if (entity != null && level.tryAddFreshEntityWithPassengers(entity)) {
                applied.put(id, shot.blob());
                rebuilt.put(id, entity);
                for (Entity rider : entity.getIndirectPassengers()) {
                    rebuilt.put(rider.getUUID(), rider);
                }
            } else {
                failedRestores++;
            }
        }
    }

    private List<Entity> present(ServerLevel level) {
        List<Entity> all = new ArrayList<>();
        Set<Entity> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (Entity entity : level.getAllEntities()) {
            all.add(entity);
            seen.add(entity);
        }
        for (Entity entity : rebuilt.values()) {
            if (entity.level() == level && !entity.isRemoved() && seen.add(entity)) {
                all.add(entity);
            }
        }
        return all;
    }

    private void maintain() {
        if (!previewing) {
            thin();
            settleSizes();
            trimToBudget();
        }

        if (lastBlob.size() > 4096) {
            Set<UUID> alive = new HashSet<>();
            for (ServerLevel level : server.getAllLevels()) {
                for (Entity entity : level.getAllEntities()) {
                    alive.add(entity.getUUID());
                }
            }
            lastBlob.keySet().retainAll(alive);
            lastTag.keySet().retainAll(alive);
        }
    }

    private void thin() {
        int[] ticks = frameTicks();
        boolean[] keep = Retention.keep(ticks, now);
        Iterator<Keyframe> it = frames.iterator();
        int i = 0;
        while (it.hasNext()) {
            Keyframe frame = it.next();
            if (!keep[i++]) {
                it.remove();
                uncount(frame);
            }
        }
    }

    private void count(Keyframe frame) {
        frameBytes += 64 + frame.entities().size() * 48L + frame.players().size() * 160L;
        for (EntityShot shot : frame.entities().values()) {
            Usage use = usage.computeIfAbsent(shot.blob(), key -> new Usage());
            if (use.refs++ == 0) {
                if (!shot.blob().packed()) {
                    unpacked.add(shot.blob());
                }
                use.bytes = shot.blob().size() + 16;
                frameBytes += use.bytes;
            }
        }
        for (PlayerState player : frame.players().values()) {
            hold(player.inventory());
            hold(player.enderChest());
        }
    }

    private void hold(ItemStack[] items) {
        Usage use = usage.computeIfAbsent(items, key -> new Usage());
        if (use.refs++ == 0) {
            use.bytes = items.length * 96L;
            frameBytes += use.bytes;
        }
    }

    private void uncount(Keyframe frame) {
        frameBytes -= 64 + frame.entities().size() * 48L + frame.players().size() * 160L;
        for (EntityShot shot : frame.entities().values()) {
            release(shot.blob());
        }
        for (PlayerState player : frame.players().values()) {
            release(player.inventory());
            release(player.enderChest());
        }
    }

    private void release(Object shared) {
        Usage use = usage.get(shared);
        if (use != null && --use.refs == 0) {
            usage.remove(shared);
            frameBytes -= use.bytes;
        }
    }

    private void settleSizes() {
        Iterator<Blob> it = unpacked.iterator();
        while (it.hasNext()) {
            Blob blob = it.next();
            if (!blob.packed()) {
                continue;
            }
            it.remove();
            Usage use = usage.get(blob);
            if (use != null) {
                long bytes = blob.size() + 16;
                frameBytes += bytes - use.bytes;
                use.bytes = bytes;
            }
        }
    }

    private void trimToBudget() {
        long budget = RewindSettings.historyBudget();
        if (frameBytes + blocks.bytes() <= budget || frames.size() < 2) {
            return;
        }
        long target = budget * 9 / 10;
        int drop = 0;
        while (frames.size() - drop > 2 && frameBytes + blocks.bytesAfter(frames.get(drop).tick()) > target) {
            uncount(frames.get(drop));
            drop++;
        }
        frames.subList(0, drop).clear();
        int first = frames.get(0).tick();
        blocks.dropUpTo(first);
        markers.removeIf(marker -> marker.tick() < first);
        born.values().removeIf(t -> t < first);
        died.values().removeIf(t -> t < first);
        Rewind.LOGGER.info("Rewind history reached its memory budget; kept the last {} seconds", (now - first) / 20);
    }
}
