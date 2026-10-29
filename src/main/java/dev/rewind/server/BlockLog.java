package dev.rewind.server;

import dev.rewind.core.Chunks;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockUpdatePacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

final class BlockLog {
    record Key(ResourceKey<Level> dimension, long pos) {
    }

    record ChunkKey(ResourceKey<Level> dimension, long chunk) {
        static ChunkKey of(ResourceKey<Level> dimension, long pos) {
            return new ChunkKey(dimension, Chunks.pack(BlockPos.getX(pos) >> 4, BlockPos.getZ(pos) >> 4));
        }
    }

    record Change(int tick, ResourceKey<Level> dimension, long pos,
                  BlockState beforeState, CompoundTag beforeData, BlockState afterState, CompoundTag afterData,
                  long logged) {
    }

    private static final class Pending {
        final BlockState state;
        final CompoundTag data;
        boolean stateChanged;

        Pending(BlockState state, CompoundTag data, boolean stateChanged) {
            this.state = state;
            this.data = data;
            this.stateChanged = stateChanged;
        }
    }

    private final MinecraftServer server;
    private final Map<Key, CompoundTag> shadow = new HashMap<>();
    private final Map<Key, Pending> pending = new LinkedHashMap<>();
    private final ArrayList<Change> changes = new ArrayList<>();

    private final Set<ChunkKey> lagging = new HashSet<>();
    private long bytes;
    private long logged;

    BlockLog(MinecraftServer server) {
        this.server = server;
    }

    void blockChanging(ServerLevel level, BlockPos pos, BlockState current, BlockEntity blockEntity) {
        Key key = new Key(level.dimension(), pos.asLong());
        Pending entry = pending.get(key);
        if (entry != null) {
            entry.stateChanged = true;
            return;
        }
        CompoundTag data = shadow.get(key);
        if (data == null && blockEntity != null) {
            data = save(level, blockEntity);
        }
        pending.put(key, new Pending(current, data, true));
    }

    void blockEntityChanged(ServerLevel level, BlockEntity blockEntity) {
        Key key = new Key(level.dimension(), blockEntity.getBlockPos().asLong());
        if (pending.containsKey(key)) {
            return;
        }
        CompoundTag data = shadow.get(key);
        if (data == null) {
            shadow.put(key, save(level, blockEntity));
            return;
        }
        pending.put(key, new Pending(blockEntity.getBlockState(), data, false));
    }

    void blockEntityLoaded(ServerLevel level, BlockEntity blockEntity) {
        Key key = new Key(level.dimension(), blockEntity.getBlockPos().asLong());
        if (!pending.containsKey(key)) {
            shadow.put(key, save(level, blockEntity));
        }
    }

    void blockEntityRemoved(ServerLevel level, BlockEntity blockEntity) {
        shadow.remove(new Key(level.dimension(), blockEntity.getBlockPos().asLong()));
    }

    void flush(int tick, boolean all) {
        Iterator<Map.Entry<Key, Pending>> it = pending.entrySet().iterator();
        while (it.hasNext()) {
            Map.Entry<Key, Pending> e = it.next();
            Pending entry = e.getValue();
            if (!all && !entry.stateChanged) {
                continue;
            }
            it.remove();
            Key key = e.getKey();
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null) {
                continue;
            }
            BlockPos pos = BlockPos.of(key.pos());
            LevelChunk chunk = level.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
            if (chunk == null) {
                continue;
            }
            BlockState after = chunk.getBlockState(pos);
            BlockEntity blockEntity = chunk.getBlockEntities().get(pos);
            CompoundTag afterData = blockEntity == null ? null : save(level, blockEntity);
            remember(key, afterData);
            if (after == entry.state && Objects.equals(afterData, entry.data)) {
                continue;
            }
            long cost = cost(entry.data) + cost(afterData);
            logged += cost;
            bytes += cost;
            changes.add(new Change(tick, key.dimension(), key.pos(), entry.state, entry.data, after, afterData, logged));
        }
    }

    void move(int from, int to, int present) {
        Set<Key> touched = new LinkedHashSet<>();
        step(from, to, null, touched);
        catchUp(to, present, false, touched);
        send(touched);
    }

    void settle(int tick, int present) {
        Set<Key> touched = new LinkedHashSet<>();
        catchUp(tick, present, true, touched);
        send(touched);
    }

    void endPreview() {
        lagging.clear();
    }

    private void catchUp(int to, int present, boolean load, Set<Key> touched) {
        Set<ChunkKey> ready = new HashSet<>();
        for (ChunkKey chunk : lagging) {
            if (load || loaded(chunk)) {
                ready.add(chunk);
            }
        }
        if (!ready.isEmpty()) {
            lagging.removeAll(ready);
            step(present, to, ready, touched);
        }
    }

    private void step(int from, int to, Set<ChunkKey> only, Set<Key> touched) {
        Map<Key, Change> last = new LinkedHashMap<>();
        Map<ChunkKey, Boolean> covered = new HashMap<>();
        if (to < from) {
            for (int i = upper(from) - 1; i >= 0 && changes.get(i).tick() > to; i--) {
                Change c = changes.get(i);
                if (covers(c, only, covered)) {
                    last.put(new Key(c.dimension(), c.pos()), c);
                }
            }
            for (Change c : last.values()) {
                apply(c.dimension(), c.pos(), c.beforeState(), c.beforeData());
            }
        } else if (to > from) {
            for (int i = upper(from); i < changes.size() && changes.get(i).tick() <= to; i++) {
                Change c = changes.get(i);
                if (covers(c, only, covered)) {
                    last.put(new Key(c.dimension(), c.pos()), c);
                }
            }
            for (Change c : last.values()) {
                apply(c.dimension(), c.pos(), c.afterState(), c.afterData());
            }
        }
        touched.addAll(last.keySet());
    }

    private boolean covers(Change c, Set<ChunkKey> only, Map<ChunkKey, Boolean> covered) {
        ChunkKey chunk = ChunkKey.of(c.dimension(), c.pos());
        Boolean known = covered.get(chunk);
        if (known != null) {
            return known;
        }
        boolean covers;
        if (only != null) {
            covers = only.contains(chunk);
        } else if (lagging.contains(chunk)) {
            covers = false;
        } else if (!loaded(chunk)) {
            lagging.add(chunk);
            covers = false;
        } else {
            covers = true;
        }
        covered.put(chunk, covers);
        return covers;
    }

    private boolean loaded(ChunkKey chunk) {
        ServerLevel level = server.getLevel(chunk.dimension());
        return level != null
                && level.getChunkSource().getChunkNow(Chunks.x(chunk.chunk()), Chunks.z(chunk.chunk())) != null;
    }

    private void send(Set<Key> touched) {
        for (Key key : touched) {
            ServerLevel level = server.getLevel(key.dimension());
            if (level == null || level.players().isEmpty()) {
                continue;
            }
            BlockPos pos = BlockPos.of(key.pos());
            BlockState state = level.getBlockState(pos);
            BlockEntity blockEntity = level.getBlockEntity(pos);
            Packet<ClientGamePacketListener> update = blockEntity == null ? null : blockEntity.getUpdatePacket();
            for (ServerPlayer player : level.players()) {
                player.connection.send(new ClientboundBlockUpdatePacket(pos, state));
                if (update != null) {
                    player.connection.send(update);
                }
            }
        }
    }

    void truncateAfter(int tick) {
        bytes -= bytesAfter(tick);
        changes.subList(upper(tick), changes.size()).clear();
    }

    void dropUpTo(int tick) {
        bytes = bytesAfter(tick);
        changes.subList(0, upper(tick)).clear();
    }

    int count() {
        return changes.size();
    }

    long bytes() {
        return bytes;
    }

    long bytesAfter(int tick) {
        int first = upper(tick);
        if (first == 0) {
            return bytes;
        }
        if (first == changes.size()) {
            return 0;
        }
        return changes.get(changes.size() - 1).logged() - changes.get(first - 1).logged();
    }

    private void apply(ResourceKey<Level> dimension, long packed, BlockState state, CompoundTag data) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            return;
        }
        BlockPos pos = BlockPos.of(packed);
        BlockWriter.set(level, pos, state);
        if (data != null) {
            BlockEntity blockEntity = level.getBlockEntity(pos);
            if (blockEntity != null) {
                Nbt.loadBlockEntity(level, blockEntity, data);
            } else {
                blockEntity = Nbt.createBlockEntity(level, pos, state, data);
                if (blockEntity != null) {
                    level.setBlockEntity(blockEntity);
                }
            }
            if (blockEntity != null) {
                blockEntity.setChanged();
            }
        }
        remember(new Key(dimension, packed), data);
    }

    private void remember(Key key, CompoundTag data) {
        if (data == null) {
            shadow.remove(key);
        } else {
            shadow.put(key, data);
        }
    }

    private int upper(int tick) {
        int lo = 0;
        int hi = changes.size();
        while (lo < hi) {
            int mid = (lo + hi) >>> 1;
            if (changes.get(mid).tick() <= tick) {
                lo = mid + 1;
            } else {
                hi = mid;
            }
        }
        return lo;
    }

    long recountedBytes() {
        long total = 0;
        for (Change c : changes) {
            total += cost(c.beforeData()) + cost(c.afterData());
        }
        return total;
    }

    private static long cost(CompoundTag data) {
        return 64 + (data == null ? 0 : data.sizeInBytes());
    }

    private static CompoundTag save(ServerLevel level, BlockEntity blockEntity) {
        return Nbt.saveBlockEntity(level, blockEntity);
    }
}
