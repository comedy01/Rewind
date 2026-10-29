package dev.rewind.selftest;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rewind.RewindSettings;
import dev.rewind.client.RewindClient;
import dev.rewind.client.gui.RewindScreen;
import dev.rewind.client.gui.SettingsScreen;
import dev.rewind.core.Chunks;
import dev.rewind.core.Marker;
import dev.rewind.core.Retention;
import dev.rewind.core.TimelineView;
import dev.rewind.server.Recorder;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Screenshot;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.events.ContainerEventHandler;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.client.tutorial.TutorialSteps;
import net.minecraft.core.BlockPos;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.ChatVisiblity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Stream;

public final class RewindSelfTest {
    private static final String WORLD = "rewind-selftest";
    private static final int TIMEOUT = 2400;
    private static final BlockPos CHEST = new BlockPos(6, -60, -5);
    private static final BlockPos GOLD = new BlockPos(8, -60, -4);
    private static final BlockPos LATER = new BlockPos(9, -60, -4);
    private static final Vec3 LOOKOUT = new Vec3(0.5, -60, -6.5);
    private static final Vec3 DEATH_SPOT = new Vec3(10.5, -60, -8.5);
    private static final Vec3 FAR_SPOT = new Vec3(400.5, -60, 400.5);

    private static volatile int frames;

    private final List<Step> steps = new ArrayList<>();
    private final List<String> shots = new ArrayList<>();
    private final Set<String> existingShots = new HashSet<>();
    private boolean started;
    private boolean finished;
    private int idle;
    private int index;
    private int delay;
    private int waited;
    private int seenFrames;
    private int patience;

    private final List<BlockPos> bricks = new ArrayList<>();
    private BlockPos broken;
    private int beforeTick;
    private long beforeClock;
    private long afterClock;
    private int presentTick;
    private int committedTick;
    private int changesAfterCommit;
    private RewindScreen screen;
    private int stressStart;
    private int stressMobs;
    private int farStart;
    private int netherStart;
    private volatile UUID farDiamond;
    private static final int STRESS_MOBS = 150;
    private static final int STRESS_SECONDS = 20;

    private static final int SOAK_MINUTES = Integer.getInteger("rewind.soak", 0);
    private static final int SOAK_STEP = 20 * 30;
    private static final int SOAK_CHECKPOINT = 20;
    private static final int FROZEN = 300;
    private static final int LONG_WAIT = 60 * 60 * 30;
    private static final String[] BUILD_BLOCKS = {"stone", "oak_planks", "glass", "cobblestone", "air", "bricks", "oak_log", "air"};

    private final Map<String, Snapshot> snapshots = new HashMap<>();
    private final Random soakRandom = new Random(11);
    private int sprintTarget;
    private int checkpointTick;
    private long checkpointStarted;
    private long heapWithHistory;
    private long openedAt;

    private record Step(int delay, BooleanSupplier ready, Runnable action, int timeout) {
    }

    private record Snapshot(int from, int tick, int[] blocks, Map<Long, String> containers, Map<UUID, String> entities) {
    }

    public void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (finished) {
            return;
        }
        if (!started) {
            if (mc.level == null && Screens.overlay(mc) == null && Screens.current(mc) != null && ++idle > 40) {
                started = true;
                mc.options.pauseOnLostFocus = false;
                mc.options.tutorialStep = TutorialSteps.NONE;
                mc.options.chatVisibility().set(ChatVisiblity.HIDDEN);
                rememberShots(mc);
                plan(mc);
                delay = steps.get(0).delay();
                log("creating world");
                deleteWorld(mc, WORLD);
                Worlds.create(mc, WORLD, true, 0L);
            }
            return;
        }
        int drawn = frames;
        if (drawn == seenFrames) {
            return;
        }
        seenFrames = drawn;
        if (delay > 0) {
            delay--;
            return;
        }
        Step step = steps.get(index);
        if (!step.ready().getAsBoolean()) {
            if (++waited > step.timeout()) {
                finish(mc, new AssertionError("timed out at step " + index));
            }
            return;
        }
        waited = 0;
        index++;
        try {
            step.action().run();
        } catch (Throwable e) {
            finish(mc, e);
            return;
        }
        if (index >= steps.size()) {
            finish(mc, null);
        } else {
            delay = steps.get(index).delay();
        }
    }

    public static void frame() {
        frames++;
    }

    private void plan(Minecraft mc) {
        when(() -> mc.level != null && mc.player != null && Screens.current(mc) == null
                && mc.getSingleplayerServer() != null, () -> log("world loaded"));
        if (SOAK_MINUTES > 0) {
            soak(mc);
            return;
        }
        then(40, () -> {
            check(Stream.of(mc.options.keyMappings).anyMatch(k -> k == RewindClient.key()
                    && k.getName().equals("key.rewind.open")), "the Rewind key is not registered");
            run(mc, Worlds.noMobSpawning());
            run(mc, "time set 1000");
            run(mc, "weather clear");
            run(mc, String.format(Locale.ROOT, "tp @p %s %s %s 0 15", LOOKOUT.x, LOOKOUT.y, LOOKOUT.z));
            run(mc, "fill -2 -60 3 2 -57 7 minecraft:stone_bricks hollow");
            run(mc, String.format(Locale.ROOT, "setblock %d %d %d minecraft:chest", CHEST.getX(), CHEST.getY(), CHEST.getZ()));
            run(mc, String.format(Locale.ROOT, "item replace block %d %d %d container.0 with minecraft:diamond 5",
                    CHEST.getX(), CHEST.getY(), CHEST.getZ()));
            summon(mc, "minecraft:cow", 4.5, -60, 1.5, "{NoAI:1b,PersistenceRequired:1b}", "Bessie");
            run(mc, "summon minecraft:pig -4.5 -60 0.5 {PersistenceRequired:1b}");
            run(mc, "clear @p");
            run(mc, "give @p minecraft:iron_sword");
            run(mc, "give @p minecraft:bread 12");
            run(mc, "xp set @p 5 levels");
        });
        within(80, () -> find(mc, "minecraft:cow") != null && find(mc, "minecraft:pig") != null, () -> {
            check(find(mc, "minecraft:cow") != null, "the cow never showed up on the client");
            bricks.addAll(onServer(mc, () -> {
                List<BlockPos> found = new ArrayList<>();
                for (BlockPos pos : BlockPos.betweenClosed(-2, -60, 3, 2, -57, 7)) {
                    if (overworld(mc).getBlockState(pos).is(Blocks.STONE_BRICKS)) {
                        found.add(pos.immutable());
                    }
                }
                return found;
            }));
            check(bricks.size() > 50, "the house was not built: " + bricks.size());
        });

        then(80, () -> {
            beforeTick = onServer(mc, () -> recorder().now());
            beforeClock = onServer(mc, () -> Worlds.time(overworld(mc)));
            log("before tick " + beforeTick + ", clock " + beforeClock);
            screenshot(mc, "before");
        });
        then(10, () -> {
            run(mc, "summon minecraft:tnt 0.5 -60 2.5 {fuse:1}");
            run(mc, "kill @e[type=minecraft:cow]");
            run(mc, String.format(Locale.ROOT, "item replace block %d %d %d container.0 with minecraft:dirt 1",
                    CHEST.getX(), CHEST.getY(), CHEST.getZ()));
            run(mc, "clear @p");
            run(mc, "xp set @p 0 levels");
            run(mc, "damage @p 7 minecraft:generic");
            run(mc, "time add 6000");
            run(mc, "weather rain");
            run(mc, String.format(Locale.ROOT, "setblock %d %d %d minecraft:gold_block", GOLD.getX(), GOLD.getY(), GOLD.getZ()));
        });
        within(100, () -> brokenBricks(mc) > 5 && find(mc, "minecraft:cow") == null, () -> {
            int lost = brokenBricks(mc);
            check(lost > 5, "the TNT did not break the house: " + lost);
            broken = onServer(mc, () -> {
                for (BlockPos pos : bricks) {
                    if (overworld(mc).getBlockState(pos).isAir()) {
                        return pos;
                    }
                }
                return null;
            });
            check(broken != null, "no broken brick found");
            check(find(mc, "minecraft:cow") == null, "the cow is still alive");
            check(serverPlayer(mc).getHealth() < 20.0F, "the damage did not land");
            check(serverPlayer(mc).getInventory().isEmpty(), "the inventory was not cleared");
            log("TNT broke " + lost + " bricks");
        });
        then(40, () -> {
            afterClock = onServer(mc, () -> Worlds.time(overworld(mc)));
            screenshot(mc, "after-damage");
        });

        then(5, () -> {
            screen = new RewindScreen(false);
            Screens.open(mc, screen);
        });
        within(60, () -> screen.view() != null, () -> {
            TimelineView view = screen.view();
            check(view != null, "the timeline never loaded");
            check(mc.isPaused(), "the game is not paused behind the timeline");
            check(recorder().previewing(), "the server is not previewing");
            presentTick = view.now();
            check(presentTick > beforeTick, "the timeline does not reach the present");
            check(view.markers().stream().anyMatch(m -> m.kind() == Marker.Kind.EXPLOSION), "no explosion marker");
            check(view.markers().stream().anyMatch(m -> m.kind() == Marker.Kind.PET_DEATH
                    && m.label().contains("Bessie")), "no marker for the named cow: " + view.markers());
            check(screen.target() == presentTick, "the slider does not start at the present");
            screenshot(mc, "timeline-present");

            Ui.drag(screen, screen.trackX(presentTick), screen.trackX(beforeTick), screen.trackY() + 2);
            check(Math.abs(screen.target() - beforeTick) <= 12,
                    "dragging landed at " + screen.target() + " instead of near " + beforeTick);
            Marker explosion = view.markers().stream().filter(m -> m.kind() == Marker.Kind.EXPLOSION).findFirst().orElseThrow();
            int[] point = screen.markerPoint(explosion);
            Ui.click(screen, point[0], point[1]);
            check(Math.abs(screen.target() - (explosion.tick() - RewindSettings.jumpBeforeTicks())) <= 5,
                    "clicking the explosion marker went to " + screen.target() + ", explosion at " + explosion.tick());
            screen.select(beforeTick);
        });
        within(100, () -> recorder().shownCursor() == screen.target(), () -> {
            check(recorder().shownCursor() == screen.target(), "the preview never caught up with the slider");
        });
        then(10, () -> {
            check(mc.level.getBlockState(broken).is(Blocks.STONE_BRICKS), "the client does not show the house rebuilt");
            check(brokenBricks(mc) == 0, "the server house is not whole: " + brokenBricks(mc));
            check(find(mc, "minecraft:cow") != null, "the client does not show the cow back");
            check(mc.player.getHealth() >= 19.9F, "the client health is not restored: " + mc.player.getHealth());
            check(hasItem(mc.player.getInventory().getItem(0), Items.IRON_SWORD)
                    || mc.player.getInventory().contains(new ItemStack(Items.IRON_SWORD)), "the sword is not back");
            check(mc.player.experienceLevel == 5, "the client xp level is " + mc.player.experienceLevel);
            check(!mc.level.isRaining(), "it still rains on the client");
            check(chestItem(mc).is(Items.DIAMOND), "the chest does not hold diamonds: " + chestItem(mc));
            check(onServer(mc, () -> overworld(mc).getBlockState(GOLD).isAir()), "the gold block is still there");
            check(Math.abs(onServer(mc, () -> Worlds.time(overworld(mc))) - beforeClock) <= 10,
                    "the clock was not rewound");
            screenshot(mc, "preview-past");
            screen.select(presentTick);
        });
        within(100, () -> recorder().shownCursor() == presentTick, () -> {
            check(recorder().shownCursor() == presentTick, "scrubbing forward never caught up");
        });
        then(10, () -> {
            check(mc.level.getBlockState(broken).isAir(), "scrubbing forward did not break the house again");
            check(find(mc, "minecraft:cow") == null, "scrubbing forward did not remove the cow");
            check(mc.player.getHealth() < 19.9F, "scrubbing forward did not restore the damage");

            screen.select(beforeTick);
        });
        within(100, () -> recorder().shownCursor() == screen.target(), () -> Ui.key(screen, InputConstants.KEY_ESCAPE));
        within(60, () -> !recorder().previewing() && Screens.current(mc) == null, () -> {
            check(!recorder().previewing(), "cancel left the preview running");
            check(Screens.current(mc) == null, "cancel did not close the timeline");
            check(brokenBricks(mc) > 5, "cancel did not bring back the present: the house is whole");
            check(onServer(mc, () -> entities(mc, "minecraft:cow", e -> true).isEmpty()),
                    "cancel left the cow alive");
            check(chestItem(mc).is(Items.DIRT), "cancel did not restore the chest: " + chestItem(mc));
            check(serverPlayer(mc).getInventory().isEmpty(), "cancel did not restore the empty inventory");
            check(onServer(mc, () -> overworld(mc).getBlockState(GOLD).is(Blocks.GOLD_BLOCK)), "cancel lost the gold block");
            check(Math.abs(onServer(mc, () -> Worlds.time(overworld(mc))) - afterClock) <= 20,
                    "cancel did not restore the clock");
            check(recorder().now() >= presentTick, "cancel cut the timeline short");
        });

        then(20, () -> {
            screen = new RewindScreen(false);
            Screens.open(mc, screen);
        });
        within(60, () -> screen.view() != null, () -> screen.select(beforeTick));
        within(100, () -> recorder().shownCursor() == screen.target(), () -> {
            committedTick = screen.target();
            screenshot(mc, "timeline-past");
            click(mc, find(screen, "rewind.screen.commit"));
        });
        within(60, () -> !recorder().previewing() && Screens.current(mc) == null, () -> {
            check(!recorder().previewing(), "rewinding left the preview running");
            check(brokenBricks(mc) == 0, "the house is not whole after rewinding: " + brokenBricks(mc));
            List<LivingEntity> cows = onServer(mc, () -> entities(mc, "minecraft:cow", e -> true).stream()
                    .map(e -> (LivingEntity) e).toList());
            check(cows.size() == 1, "expected one cow after rewinding, found " + cows.size());
            check(cows.get(0).getHealth() == cows.get(0).getMaxHealth(), "the cow is hurt");
            check("Bessie".equals(cows.get(0).getCustomName().getString()), "the cow lost its name");
            check(onServer(mc, () -> entities(mc, "minecraft:pig", e -> true).size()) == 1,
                    "the pig is missing or doubled");
            check(itemEntities(mc) == 0, "dropped items from after the rewind point are still lying around");
            check(chestItem(mc).is(Items.DIAMOND) && chestItem(mc).getCount() == 5, "the chest was not rewound: " + chestItem(mc));
            check(onServer(mc, () -> overworld(mc).getBlockState(GOLD).isAir()), "the gold block survived the rewind");
            ServerPlayer player = serverPlayer(mc);
            check(player.getHealth() >= 19.9F, "the player health was not rewound: " + player.getHealth());
            check(player.getInventory().contains(new ItemStack(Items.IRON_SWORD)), "the sword did not come back");
            check(player.getInventory().countItem(Items.BREAD) == 12, "the bread did not come back");
            check(player.experienceLevel == 5, "the xp level was not rewound: " + player.experienceLevel);
            check(player.position().distanceTo(LOOKOUT) < 1.0, "the player was not put back: " + player.position());
            check(!onServer(mc, () -> overworld(mc).isRaining()), "the rain was not rewound");
            check(Math.abs(onServer(mc, () -> Worlds.time(overworld(mc))) - beforeClock) <= 10,
                    "the time of day was not rewound");
            int sinceCommit = recorder().now() - committedTick;
            check(sinceCommit >= 0 && sinceCommit < 40, "the timeline was not cut at the rewind point: " + sinceCommit);
            check(mc.player.getHealth() >= 19.9F, "the client still shows the old health");
            changesAfterCommit = recorder().changeCount();
            screenshot(mc, "after-rewind");
        });
        then(40, () -> {
            check(!mc.isPaused(), "the game is still paused after rewinding");
            check(recorder().now() > committedTick, "recording did not continue after rewinding");
            run(mc, String.format(Locale.ROOT, "setblock %d %d %d minecraft:diamond_block", LATER.getX(), LATER.getY(), LATER.getZ()));
        });
        within(40, () -> recorder().changeCount() > changesAfterCommit, () ->
                check(recorder().changeCount() > changesAfterCommit, "a block placed after rewinding was not recorded"));

        then(10, () -> {
            run(mc, String.format(Locale.ROOT, "tp @p %s %s %s 90 10", DEATH_SPOT.x, DEATH_SPOT.y, DEATH_SPOT.z));
            run(mc, "give @p minecraft:golden_apple 3");
        });
        then(100, () -> run(mc, "kill @p"));
        within(100, () -> Screens.current(mc) instanceof DeathScreen, () -> {
            check(Screens.current(mc) instanceof DeathScreen, "no death screen");
        });
        then(30, () -> {
            screenshot(mc, "death-screen");
            click(mc, find(Screens.current(mc), "rewind.death.button"));
        });
        within(300, () -> Screens.current(mc) instanceof RewindScreen s && s.view() != null, () -> {
            screen = (RewindScreen) Screens.current(mc);
            Marker death = screen.view().lastDeath();
            check(death != null, "no death marker");
            check(Math.abs(screen.target() - (death.tick() - RewindSettings.jumpBeforeTicks())) <= 5,
                    "the timeline did not jump to just before the death: " + screen.target() + " vs " + death.tick());
        });
        within(100, () -> recorder().shownCursor() == screen.target(), () -> {
            ServerPlayer player = serverPlayer(mc);
            check(player.isAlive(), "the player is not alive in the preview");
            check(player.position().distanceTo(DEATH_SPOT) < 1.0, "the player is not back where they were: " + player.position());
            check(player.getInventory().countItem(Items.GOLDEN_APPLE) == 3, "the golden apples are not back");
            check(recorder().failedRestores() == 0, "some entities could not be put back: " + recorder().failedRestores());
            screenshot(mc, "death-preview");
            Ui.key(screen, InputConstants.KEY_RETURN);
        });
        within(60, () -> !recorder().previewing() && Screens.current(mc) == null, () -> {
            check(!recorder().previewing(), "enter did not rewind");
            check(mc.player.isAlive() && mc.player.getHealth() > 0, "the client player is dead");
            check(itemEntities(mc) == 0, "the death drops are still on the ground: " + onServer(mc, () -> {
                StringBuilder out = new StringBuilder("now " + recorder().now() + " committed " + screen.target());
                for (Entity item : entities(mc, "minecraft:item", e -> true)) {
                    out.append("; ").append(item).append(' ').append(recorder().lifeOf(item));
                }
                return out.toString();
            }));
            check(mc.player.position().distanceTo(DEATH_SPOT) < 1.0, "the client player is not at the death spot");
        });
        then(40, () -> {
            check(mc.player.isAlive(), "the player died again after rewinding");

            check(itemEntities(mc) == 0, "death drops turned up after the rewind: " + itemEntities(mc));
            screenshot(mc, "death-rewound");
        });
        stress(mc);
        farAway(mc);
        netherTrip(mc);
        settings(mc);
    }

    private void settings(Minecraft mc) {
        then(10, () -> Screens.open(mc, new SettingsScreen(null)));
        then(5, () -> {
            Screen settings = Screens.current(mc);
            check(settings instanceof SettingsScreen, "the settings screen did not open");
            click(mc, find(settings, "rewind.settings.key"));
            Ui.key(settings, InputConstants.KEY_J);
            check(RewindClient.key().saveString().equals("key.keyboard.j"),
                    "the key was not rebound: " + RewindClient.key().saveString());
        });
        then(5, () -> screenshot(mc, "settings"));
        then(5, () -> {
            Screen settings = Screens.current(mc);
            click(mc, find(settings, "controls.reset"));
            check(RewindClient.key().isDefault(), "reset did not restore the default key");
            Ui.key(settings, InputConstants.KEY_ESCAPE);
        });
        then(5, () -> {
            check(Screens.current(mc) == null, "closing the settings did not return to the game");
            check(Files.isRegularFile(mc.gameDirectory.toPath().resolve("config").resolve("rewind.properties")),
                    "the settings were not saved");
            log("settings: rebound the key, reset it and saved");
        });
    }

    private void netherTrip(Minecraft mc) {
        then(10, () -> run(mc, "execute in minecraft:the_nether run tp @p 0.5 128 0.5"));
        within(400, () -> mc.level.dimension() == Level.NETHER && Screens.current(mc) == null,
                () -> netherStart = onServer(mc, () -> recorder().now()));
        within(1200, () -> onServer(mc, () -> recorder().now()) - netherStart >= 60, () -> run(mc, String.format(Locale.ROOT, "execute in minecraft:overworld run tp @p %s %s %s",
                FAR_SPOT.x, FAR_SPOT.y, FAR_SPOT.z)));
        within(400, () -> mc.level.dimension() == Level.OVERWORLD && Screens.current(mc) == null, () -> {});
        then(40, () -> {
            screen = new RewindScreen(false);
            Screens.open(mc, screen);
        });
        within(60, () -> screen.view() != null, () -> {
            List<Marker> travel = screen.view().markers().stream().filter(m -> m.kind() == Marker.Kind.TRAVEL).toList();
            check(travel.size() == 2, "expected two dimension markers, found " + travel);
            check(travel.get(0).label().equals("Entered the Nether"), "wrong first marker: " + travel.get(0).label());
            check(travel.get(1).label().equals("Entered the Overworld"), "wrong second marker: " + travel.get(1).label());
            check(screen.view().elsewhere(screen.view().now()) == null, "the present is flagged as elsewhere");
            check(travel.get(1).tick() - travel.get(0).tick() >= 2 * Retention.FRAME_STEP,
                    "the Nether stretch is too short to land in: " + travel);
            screen.select((travel.get(0).tick() + travel.get(1).tick()) / 2);
        });
        within(200, () -> recorder().shownCursor() == screen.target(), () -> {
            check("minecraft:the_nether".equals(screen.view().elsewhere(screen.target())),
                    "the Nether stretch is not flagged: " + screen.view().elsewhere(screen.target()));
            check(mc.level.dimension() == Level.OVERWORLD, "the preview moved the player to another dimension");
            check(Screens.current(mc) == screen, "the preview closed the timeline");
            committedTick = screen.target();
            screenshot(mc, "nether-stretch");
            Ui.key(screen, InputConstants.KEY_RETURN);
        });
        within(400, () -> !recorder().previewing() && mc.level.dimension() == Level.NETHER && Screens.current(mc) == null,
                () -> check(mc.level.dimension() == Level.NETHER, "rewinding into the Nether stretch did not go there"));
        then(40, () -> {
            screen = new RewindScreen(false);
            Screens.open(mc, screen);
        });
        within(60, () -> screen.view() != null, () -> {
            check(screen.view().markers().stream().noneMatch(m -> m.kind() == Marker.Kind.TRAVEL && m.tick() > committedTick),
                    "rewinding into the Nether left a travel marker behind");
            Ui.key(screen, InputConstants.KEY_ESCAPE);
        });
        within(200, () -> !recorder().previewing() && Screens.current(mc) == null,
                () -> log("nether trip: marked, flagged, previewed in place and rewound into the Nether"));
    }

    private void farAway(Minecraft mc) {
        long spot = Chunks.pack((int) Math.floor(FAR_SPOT.x) >> 4, (int) Math.floor(FAR_SPOT.z) >> 4);
        then(10, () -> run(mc, String.format(Locale.ROOT, "tp @p %s %s %s", FAR_SPOT.x, FAR_SPOT.y, FAR_SPOT.z)));
        within(200, () -> onServer(mc, () -> overworld(mc).areEntitiesLoaded(spot)), () -> {
            IntegratedServer server = mc.getSingleplayerServer();
            server.execute(() -> {
                ItemEntity diamond = new ItemEntity(overworld(mc), FAR_SPOT.x + 2, FAR_SPOT.y, FAR_SPOT.z,
                        new ItemStack(Items.DIAMOND));
                diamond.setPickUpDelay(32767);
                farDiamond = diamond.getUUID();
                overworld(mc).addFreshEntity(diamond);
            });
            summon(mc, "minecraft:pig", FAR_SPOT.x + 1, FAR_SPOT.y, FAR_SPOT.z + 3, "{NoAI:1b}", "Far");
        });
        then(20, () -> {
            check(diamonds(mc) == 1, "the diamond did not appear: " + diamonds(mc));
            farStart = onServer(mc, () -> recorder().now());
        });
        then(20, () -> {
            summon(mc, "minecraft:pig", FAR_SPOT.x - 3, FAR_SPOT.y, FAR_SPOT.z + 3, "{NoAI:1b}", "Late");
            run(mc, "kill @e[type=minecraft:item]");
            run(mc, String.format(Locale.ROOT, "tp @e[type=minecraft:pig,name=Far] %s %s %s",
                    FAR_SPOT.x + 5, FAR_SPOT.y, FAR_SPOT.z + 3));
        });
        then(10, () -> run(mc, "tp @p 3000 -60 3000"));
        within(1200, () -> onServer(mc, () -> !overworld(mc).areEntitiesLoaded(spot)), () -> {
            check(onServer(mc, () -> !overworld(mc).areEntitiesLoaded(spot)), "the spot never unloaded");
            screen = new RewindScreen(false);
            Screens.open(mc, screen);
        });
        within(60, () -> screen.view() != null, () -> screen.select(farStart));
        within(200, () -> recorder().shownCursor() == screen.target(), () -> {
            check(screen.target() < farStart + 20, "the timeline did not go back before the pickup: " + screen.target());
            check(diamonds(mc) == 1 || onServer(mc, () -> recorder().restored(farDiamond)),
                    "the preview did not bring the diamond back: " + diamonds(mc));
            log("far away: during the preview the far chunk's entities are "
                    + (onServer(mc, () -> overworld(mc).areEntitiesLoaded(spot)) ? "loaded" : "not loaded"));
            Ui.key(screen, InputConstants.KEY_ESCAPE);
        });
        within(200, () -> !recorder().previewing() && Screens.current(mc) == null, () -> run(mc,
                String.format(Locale.ROOT, "tp @p %s %s %s", FAR_SPOT.x, FAR_SPOT.y, FAR_SPOT.z)));
        within(600, () -> onServer(mc, () -> overworld(mc).areEntitiesLoaded(spot)), () -> {});
        then(40, () -> {
            check(diamonds(mc) == 0, "cancel left a copy of the picked-up diamond: " + diamonds(mc));
            List<Double> pigs = onServer(mc, () -> entities(mc, "minecraft:pig", e -> e.hasCustomName() && e.getCustomName().getString().equals("Far"))
                    .stream().map(Entity::getX).toList());
            check(pigs.size() == 1 && Math.abs(pigs.get(0) - (FAR_SPOT.x + 5)) < 0.1,
                    "the far pig is not where it was moved to: " + pigs);
            int late = onServer(mc, () -> entities(mc, "minecraft:pig", e -> e.hasCustomName()
                    && e.getCustomName().getString().equals("Late")).size());
            check(late == 1, "the pig born after the previewed moment did not survive the cancel: " + late);
            log("far away: cancel put the unloaded area back as it was");
        });
    }

    private void soak(Minecraft mc) {
        then(40, () -> {
            int limit = Integer.getInteger("rewind.soakLimit", 0);
            if (limit > 0) {
                RewindSettings.setHistoryLimitMb(limit);
            }
            log("soak: " + SOAK_MINUTES + " minutes of game time, history budget " + (RewindSettings.historyBudget() >> 20)
                    + " MB, max heap " + (Runtime.getRuntime().maxMemory() >> 20) + " MB");
            run(mc, "gamemode creative @p");
            run(mc, "time set 1000");
            run(mc, "weather clear");
            run(mc, "tp @p 10.5 -60 0.5 0 30");
            run(mc, "fill 20 -60 -12 44 -60 -12 minecraft:oak_fence");
            run(mc, "fill 20 -60 12 44 -60 12 minecraft:oak_fence");
            run(mc, "fill 20 -60 -12 20 -60 12 minecraft:oak_fence");
            run(mc, "fill 44 -60 -12 44 -60 12 minecraft:oak_fence");
            Random random = new Random(3);
            String[] animals = {"cow", "sheep", "chicken", "pig"};
            for (int i = 0; i < 120; i++) {
                run(mc, String.format(Locale.ROOT, "summon minecraft:%s %d -60 %d {PersistenceRequired:1b}",
                        animals[i % animals.length], 21 + random.nextInt(23), -11 + random.nextInt(23)));
            }
            summon(mc, "minecraft:cow", 30.5, -60, 0.5, "{PersistenceRequired:1b}", "Pet");
            for (int i = 0; i < 16; i++) {
                run(mc, String.format(Locale.ROOT, "setblock %d -60 -20 minecraft:furnace", -10 + i));
            }
            refillFurnaces(mc);
            String[][] ring = {{"-20", "10", "east"}, {"-19", "10", "east"}, {"-18", "10", "south"}, {"-18", "11", "south"},
                    {"-18", "12", "west"}, {"-19", "12", "west"}, {"-20", "12", "north"}, {"-20", "11", "north"}};
            for (String[] hopper : ring) {
                run(mc, String.format(Locale.ROOT, "setblock %s -60 %s minecraft:hopper[facing=%s]", hopper[0], hopper[1], hopper[2]));
            }
            String[] loot = {"cobblestone", "iron_ingot", "redstone", "oak_log", "bone"};
            for (int slot = 0; slot < loot.length; slot++) {
                run(mc, String.format(Locale.ROOT, "item replace block -20 -60 10 container.%d with minecraft:%s 64", slot, loot[slot]));
            }
            run(mc, "setblock -25 -60 -5 minecraft:observer[facing=east]");
            run(mc, "setblock -24 -60 -5 minecraft:observer[facing=west]");
            run(mc, "setblock -26 -60 -5 minecraft:redstone_lamp");
            run(mc, "fill -30 -61 20 -11 -61 39 minecraft:farmland");
            run(mc, "fill -21 -61 20 -21 -61 39 minecraft:water");
            run(mc, "fill -30 -60 20 -22 -60 39 minecraft:wheat");
            run(mc, "fill -20 -60 20 -11 -60 39 minecraft:wheat");
        });
        then(600, () -> log("soak: base built, " + onServer(mc, () -> recorder().stats())));
        freezeWindow(mc, "start");
        int steps = SOAK_MINUTES * 60 * 20 / SOAK_STEP;
        then(1, () -> {
            checkpointTick = recorder().now();
            checkpointStarted = System.nanoTime();
        });
        for (int step = 1; step <= steps; step++) {
            int number = step;
            then(1, () -> {
                soakEvent(mc, number);
                sprintTarget = onServer(mc, () -> recorder().now()) + SOAK_STEP;
                run(mc, "tick sprint " + SOAK_STEP);
            });
            await(() -> recorder().now() >= sprintTarget, () -> {});
            if (step % SOAK_CHECKPOINT == 0) {
                then(1, () -> checkpoint(mc, number * SOAK_STEP / 20 / 60));
            }
            if (step == (Integer.getInteger("rewind.soakLimit", 0) > 0 ? steps * 3 / 4 : steps / 2)) {
                freezeWindow(mc, "middle");
            }
        }
        freezeWindow(mc, "present");
        then(1, () -> {
            heapWithHistory = heapMb();
            log("soak: heap with the full history " + heapWithHistory + " MB, " + onServer(mc, () -> recorder().stats()));
            openedAt = System.nanoTime();
            screen = new RewindScreen(false);
            Screens.open(mc, screen);
        });
        within(600, () -> screen.view() != null, () -> {
            check(screen.view() != null, "the timeline never opened");
            TimelineView view = screen.view();
            log(String.format(Locale.ROOT, "soak: timeline opened in %d ms: %d s long, %d keyframes, %d markers in %d rows, estimate %.1f MB",
                    (System.nanoTime() - openedAt) / 1_000_000, (view.now() - view.start()) / 20, view.frames().length,
                    view.markers().size(), screen.markerRows(), view.bytes() / 1048576.0));
            screenshot(mc, "soak-timeline");
        });
        previewWindow(mc, "start");
        previewWindow(mc, "middle");
        then(1, () -> Ui.key(screen, InputConstants.KEY_HOME));
        await(() -> recorder().shownCursor() == screen.target(), () -> log(String.format(Locale.ROOT,
                "soak: Home went to the first moment (%d s back) in %d ms",
                (screen.view().now() - screen.target()) / 20, recorder().lastMoveMillis())));
        then(1, () -> Ui.key(screen, InputConstants.KEY_ESCAPE));
        await(() -> !recorder().previewing() && Screens.current(mc) == null, () -> {
            log("soak: cancel back to the present took " + recorder().lastMoveMillis() + " ms");
            String diff = onServer(mc, () -> difference(mc, snapshots.get("present")));
            check(diff.isEmpty(), "cancel did not bring back the present: " + diff);
            log("soak: cancel matches the present exactly");
        });
        then(20, () -> {
            screen = new RewindScreen(false);
            Screens.open(mc, screen);
        });
        String[] goal = new String[1];
        within(600, () -> screen.view() != null, () -> {
            goal[0] = snapshots.containsKey("start") ? "start" : "middle";
            check(snapshots.containsKey(goal[0]), "neither snapshot is left in the history");
            screen.select(frameInside(screen.view(), snapshots.get(goal[0])));
        });
        await(() -> recorder().shownCursor() == screen.target(), () -> Ui.key(screen, InputConstants.KEY_RETURN));
        await(() -> !recorder().previewing() && Screens.current(mc) == null, () -> {
            String diff = onServer(mc, () -> difference(mc, snapshots.get(goal[0])));
            check(diff.isEmpty(), "rewinding to '" + goal[0] + "' did not match it: " + diff);
            long heap = heapMb();
            log(String.format(Locale.ROOT, "soak: rewind here to '%s' took %d ms and matches it exactly; heap %d MB -> %d MB "
                            + "(the history after it really used about %d MB), %s",
                    goal[0], recorder().lastCommitMillis(), heapWithHistory, heap, heapWithHistory - heap,
                    onServer(mc, () -> recorder().stats())));
        });
        then(20, () -> {
            run(mc, "tick unfreeze");
            sprintTarget = onServer(mc, () -> recorder().now()) + 200;
            run(mc, "setblock 0 -60 30 minecraft:diamond_block");
        });
        await(() -> recorder().now() >= sprintTarget, () -> {
            check(onServer(mc, () -> overworld(mc).getBlockState(new BlockPos(0, -60, 30)).is(Blocks.DIAMOND_BLOCK)),
                    "the world did not keep going after the rewind");
            log("soak: recording continues after the rewind, " + onServer(mc, () -> recorder().stats()));
        });
    }

    private void soakEvent(Minecraft mc, int step) {
        int x = 50 + soakRandom.nextInt(18);
        int z = -20 + soakRandom.nextInt(38);
        String block = BUILD_BLOCKS[soakRandom.nextInt(BUILD_BLOCKS.length)];
        run(mc, String.format(Locale.ROOT, "fill %d -60 %d %d -58 %d minecraft:%s", x, z, x + 3, z + 3, block));
        if (step % 7 == 0) {
            run(mc, String.format(Locale.ROOT, "setblock %d -57 %d minecraft:chest", x, z));
            run(mc, String.format(Locale.ROOT, "item replace block %d -57 %d container.%d with minecraft:gold_ingot %d",
                    x, z, soakRandom.nextInt(27), 1 + soakRandom.nextInt(64)));
        }
        if (step % 10 == 0) {
            run(mc, String.format(Locale.ROOT, "summon minecraft:tnt %d -57 %d {fuse:1}", x + 1, z + 1));
        }
        if (step % 40 == 0) {
            run(mc, "kill @e[type=minecraft:cow,name=Pet]");
            summon(mc, "minecraft:cow", 30.5, -60, 0.5, "{PersistenceRequired:1b}", "Pet");
        }
        if (step % SOAK_CHECKPOINT == 0) {
            refillFurnaces(mc);
        }
    }

    private static void refillFurnaces(Minecraft mc) {
        for (int i = 0; i < 16; i++) {
            int x = -10 + i;
            run(mc, String.format(Locale.ROOT, "item replace block %d -60 -20 container.0 with minecraft:raw_iron 64", x));
            run(mc, String.format(Locale.ROOT, "item replace block %d -60 -20 container.1 with minecraft:coal 64", x));
            run(mc, String.format(Locale.ROOT, "item replace block %d -60 -20 container.2 with minecraft:air", x));
        }
    }

    private void checkpoint(Minecraft mc, int minutes) {
        int now = onServer(mc, () -> recorder().now());
        double seconds = (System.nanoTime() - checkpointStarted) / 1e9;
        String stats = onServer(mc, () -> recorder().stats());
        long[] sizes = onServer(mc, () -> new long[]{recorder().recountedHistoryBytes(), recorder().historyBytes()});
        long estimate = sizes[1];
        check(estimate == sizes[0], "the running history size drifted: " + estimate + " vs recounted " + sizes[0]);
        int first = onServer(mc, () -> recorder().firstTick());
        int entities = onServer(mc, () -> {
            int count = 0;
            for (Entity ignored : overworld(mc).getAllEntities()) {
                count++;
            }
            return count;
        });
        onServer(mc, () -> {
            recorder().resetMaxima();
            return null;
        });
        log(String.format(Locale.ROOT, "soak %3d min: %.0f ticks/s, history %d s long, estimate %.1f MB, heap %d MB, %d entities, %s",
                minutes, (now - checkpointTick) / seconds, (now - first) / 20, estimate / 1048576.0, heapMb(), entities, stats));
        checkpointTick = now;
        checkpointStarted = System.nanoTime();
    }

    private void freezeWindow(Minecraft mc, String name) {
        int[] from = new int[1];
        then(1, () -> {
            run(mc, "tick freeze");
            from[0] = onServer(mc, () -> recorder().now());
            sprintTarget = from[0] + FROZEN;
        });
        await(() -> recorder().now() >= sprintTarget - FROZEN / 2, () ->
                snapshots.put(name + "-early", onServer(mc, () -> snapshot(mc, from[0]))));
        await(() -> recorder().now() >= sprintTarget, () -> {
            Snapshot shot = onServer(mc, () -> snapshot(mc, from[0]));
            String drift = onServer(mc, () -> difference(mc, snapshots.get(name + "-early")));
            check(drift.isEmpty(), "the world kept changing while frozen: " + drift);
            snapshots.put(name, shot);
            log("soak: snapshot '" + name + "' at tick " + shot.tick() + ": " + shot.entities().size() + " entities, "
                    + shot.containers().size() + " containers, " + onServer(mc, () -> recorder().stats()));
            if (!name.equals("present")) {
                run(mc, "tick unfreeze");
            }
        });
    }

    private static int frameInside(TimelineView view, Snapshot shot) {
        int found = -1;
        for (int frame : view.frames()) {
            if (frame > shot.from() && frame <= shot.tick()) {
                found = frame;
            }
        }
        return found;
    }

    private void previewWindow(Minecraft mc, String name) {
        then(1, () -> {
            int frame = frameInside(screen.view(), snapshots.get(name));
            if (frame < 0) {
                log("soak: '" + name + "' is no longer in the history (it starts at tick " + screen.view().start() + ")");
                snapshots.remove(name);
                return;
            }
            screen.select(frame);
        });
        await(() -> !snapshots.containsKey(name) || recorder().shownCursor() == screen.target(), () -> {
            Snapshot shot = snapshots.get(name);
            if (shot == null) {
                return;
            }
            int landed = recorder().shownCursor();
            log(String.format(Locale.ROOT, "soak: preview of '%s' (%d s back) took %d ms, landed on tick %d",
                    name, (screen.view().now() - landed) / 20, recorder().lastMoveMillis(), landed));
            check(landed > shot.from() && landed <= shot.tick(),
                    "the preview landed outside the frozen stretch " + shot.from() + ".." + shot.tick() + ": " + landed);
            String diff = onServer(mc, () -> difference(mc, shot));
            check(diff.isEmpty(), "the preview of '" + name + "' does not match it: " + diff);
            log("soak: preview of '" + name + "' matches it exactly");
            screenshot(mc, "soak-" + name);
        });
    }

    private static Snapshot snapshot(Minecraft mc, int from) {
        ServerLevel level = overworld(mc);
        List<Integer> states = new ArrayList<>();
        Map<Long, String> containers = new HashMap<>();
        for (BlockPos pos : BlockPos.betweenClosed(-48, -64, -48, 72, -44, 48)) {
            BlockState state = level.getBlockState(pos);
            states.add(Block.getId(state));
            if (state.hasBlockEntity() && level.getBlockEntity(pos) instanceof Container container) {
                StringBuilder items = new StringBuilder();
                for (int slot = 0; slot < container.getContainerSize(); slot++) {
                    ItemStack stack = container.getItem(slot);
                    if (!stack.isEmpty()) {
                        items.append(slot).append('=').append(BuiltInRegistries.ITEM.getKey(stack.getItem()))
                                .append('x').append(stack.getCount()).append(' ');
                    }
                }
                containers.put(pos.asLong(), items.toString());
            }
        }
        Map<UUID, String> entities = new HashMap<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Player || entity.isRemoved()) {
                continue;
            }
            String extra = entity instanceof LivingEntity living ? " hp " + living.getHealth()
                    : entity instanceof ItemEntity item ? " " + BuiltInRegistries.ITEM.getKey(item.getItem().getItem())
                    + "x" + item.getItem().getCount() : "";
            entities.put(entity.getUUID(), String.format(Locale.ROOT, "%s %.3f %.3f %.3f%s",
                    BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()), entity.getX(), entity.getY(), entity.getZ(), extra));
        }
        return new Snapshot(from, recorder().now(), states.stream().mapToInt(Integer::intValue).toArray(), containers, entities);
    }

    private static String difference(Minecraft mc, Snapshot expected) {
        Snapshot actual = snapshot(mc, expected.from());
        List<String> out = new ArrayList<>();
        int blocks = 0;
        int i = 0;
        for (BlockPos pos : BlockPos.betweenClosed(-48, -64, -48, 72, -44, 48)) {
            if (expected.blocks()[i] != actual.blocks()[i] && blocks++ < 5) {
                out.add(pos.toShortString() + " expected " + Block.stateById(expected.blocks()[i])
                        + " got " + Block.stateById(actual.blocks()[i]));
            }
            i++;
        }
        int containers = 0;
        Set<Long> positions = new HashSet<>(expected.containers().keySet());
        positions.addAll(actual.containers().keySet());
        for (long pos : positions) {
            if (!Objects.equals(expected.containers().get(pos), actual.containers().get(pos)) && containers++ < 5) {
                out.add("container " + BlockPos.of(pos).toShortString() + " expected [" + expected.containers().get(pos)
                        + "] got [" + actual.containers().get(pos) + "]");
            }
        }
        int missing = 0;
        int changed = 0;
        int extra = 0;
        for (Map.Entry<UUID, String> entry : expected.entities().entrySet()) {
            String now = actual.entities().get(entry.getKey());
            if (now == null) {
                if (missing++ < 5) {
                    out.add("missing " + entry.getValue());
                }
            } else if (!now.equals(entry.getValue()) && changed++ < 5) {
                out.add("changed " + entry.getValue() + " -> " + now);
            }
        }
        for (Map.Entry<UUID, String> entry : actual.entities().entrySet()) {
            if (!expected.entities().containsKey(entry.getKey()) && extra++ < 5) {
                out.add("extra " + entry.getValue());
            }
        }
        if (blocks + containers + missing + changed + extra > 0) {
            out.add(0, String.format(Locale.ROOT, "%d blocks, %d containers differ; %d entities missing, %d changed, %d extra",
                    blocks, containers, missing, changed, extra));
        }
        return String.join("; ", out);
    }

    private static long heapMb() {
        System.gc();
        Runtime runtime = Runtime.getRuntime();
        return (runtime.totalMemory() - runtime.freeMemory()) >> 20;
    }

    private static int diamonds(Minecraft mc) {
        return onServer(mc, () -> entities(mc, "minecraft:item",
                e -> ((ItemEntity) e).getItem().is(Items.DIAMOND)).size());
    }

    private void stress(Minecraft mc) {
        then(10, () -> {
            stressStart = onServer(mc, () -> recorder().now());
            java.util.Random random = new java.util.Random(7);
            for (int i = 0; i < STRESS_MOBS; i++) {
                run(mc, String.format(Locale.ROOT, "summon minecraft:%s %d -60 %d {PersistenceRequired:1b}",
                        i % 2 == 0 ? "sheep" : "chicken", random.nextInt(60) - 30, random.nextInt(60) - 20));
            }
        });
        for (int second = 0; second < STRESS_SECONDS; second++) {
            int layer = second;
            then(20, () -> run(mc, String.format(Locale.ROOT, "fill -12 -61 %d 12 -61 %d minecraft:%s",
                    -30 + layer % 10, -30 + layer % 10, layer % 2 == 0 ? "sand" : "gravel")));
        }
        then(5, () -> {
            screen = new RewindScreen(false);
            Screens.open(mc, screen);
        });
        within(60, () -> screen.view() != null, () -> {
            stressMobs = onServer(mc, () -> entities(mc, "minecraft:sheep", e -> true).size()
                    + entities(mc, "minecraft:chicken", e -> true).size());

            screen.select(stressStart - Retention.FRAME_STEP);
        });
        within(400, () -> recorder().shownCursor() == screen.target(), () -> {
            long capture = recorder().averageCaptureMicros();
            long move = recorder().lastMoveMillis();
            log(String.format(Locale.ROOT,
                    "stress: %d mobs, %d block changes, history %.1f MB, keyframe %d us avg, scrub %d s back took %d ms",
                    stressMobs, recorder().changeCount(), screen.view().bytes() / 1048576.0, capture,
                    (screen.view().now() - screen.target()) / 20, move));
            check(stressMobs >= STRESS_MOBS - 5, "the stress mobs did not spawn: " + stressMobs);
            check(capture < 10_000, "keyframes are too slow: " + capture + " us");
            check(move < 3_000, "scrubbing back is too slow: " + move + " ms");
            int present = onServer(mc, () -> entities(mc, "minecraft:sheep", e -> true).size()
                    + entities(mc, "minecraft:chicken", e -> true).size());
            check(present == 0, "the stress mobs should not exist yet at that moment: " + present + " (" + onServer(mc, () -> {
                StringBuilder out = new StringBuilder("start " + stressStart + " shown " + recorder().shownCursor());
                entities(mc, "minecraft:sheep", e -> true).stream().limit(3)
                        .forEach(e -> out.append("; ").append(e).append(' ').append(recorder().lifeOf(e)));
                return out.toString();
            }) + ")");
            screenshot(mc, "stress-past");
            Ui.key(screen, InputConstants.KEY_ESCAPE);
        });
        within(200, () -> !recorder().previewing() && Screens.current(mc) == null, () -> {
            int back = onServer(mc, () -> entities(mc, "minecraft:sheep", e -> true).size()
                    + entities(mc, "minecraft:chicken", e -> true).size());
            check(back == stressMobs, "cancel did not bring every mob back: " + back + " of " + stressMobs);
            log("stress: cancel restored " + back + " mobs in " + recorder().lastMoveMillis() + " ms");
        });
    }

    private static Recorder recorder() {
        return RewindClient.recorder(Minecraft.getInstance());
    }

    private static ServerLevel overworld(Minecraft mc) {
        return mc.getSingleplayerServer().overworld();
    }

    private static ServerPlayer serverPlayer(Minecraft mc) {
        return onServer(mc, () -> mc.getSingleplayerServer().getPlayerList().getPlayers().get(0));
    }

    private int brokenBricks(Minecraft mc) {
        return onServer(mc, () -> {
            int broken = 0;
            for (BlockPos pos : bricks) {
                if (!overworld(mc).getBlockState(pos).is(Blocks.STONE_BRICKS)) {
                    broken++;
                }
            }
            return broken;
        });
    }

    private static ItemStack chestItem(Minecraft mc) {
        return onServer(mc, () -> ((Container) overworld(mc).getBlockEntity(CHEST)).getItem(0).copy());
    }

    private static int itemEntities(Minecraft mc) {
        return onServer(mc, () -> entities(mc, "minecraft:item", e -> true).size());
    }

    private static boolean hasItem(ItemStack stack, net.minecraft.world.item.Item item) {
        return stack.is(item);
    }

    private static List<Entity> entities(Minecraft mc, String type, Predicate<Entity> filter) {
        List<Entity> found = new ArrayList<>();
        for (Entity entity : overworld(mc).getAllEntities()) {
            if (!entity.isRemoved() && BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(type)
                    && filter.test(entity)) {
                found.add(entity);
            }
        }
        return found;
    }

    private static void summon(Minecraft mc, String type, double x, double y, double z, String nbt, String name) {
        run(mc, String.format(Locale.ROOT, "summon %s %s %s %s %s", type, x, y, z, nbt));
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> {
            for (Entity entity : entities(mc, type, e -> !e.hasCustomName() && e.distanceToSqr(x, y, z) < 1.0)) {
                entity.setCustomName(Component.literal(name));
            }
        });
    }

    private static <T> T onServer(Minecraft mc, Supplier<T> query) {
        IntegratedServer server = mc.getSingleplayerServer();
        return server.submit(query::get).join();
    }

    private static Entity find(Minecraft mc, String type) {
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (BuiltInRegistries.ENTITY_TYPE.getKey(entity.getType()).toString().equals(type) && entity.isAlive()) {
                return entity;
            }
        }
        return null;
    }

    private void then(int ticks, Runnable action) {
        steps.add(new Step(ticks, () -> true, action, TIMEOUT));
    }

    private void when(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(0, ready, action, TIMEOUT));
    }

    private void await(BooleanSupplier ready, Runnable action) {
        steps.add(new Step(0, ready, action, LONG_WAIT));
    }

    private void within(int ticks, BooleanSupplier ready, Runnable action) {
        then(1, () -> patience = ticks);
        when(() -> ready.getAsBoolean() || --patience < 0, action);
    }

    private void finish(Minecraft mc, Throwable failure) {
        finished = true;
        nameShots(mc);
        if (failure == null) {
            log("ALL CHECKS PASSED");
        } else {
            log("FAILED: " + failure);
            failure.printStackTrace();
        }
        mc.stop();
    }

    static void run(Minecraft mc, String command) {
        IntegratedServer server = mc.getSingleplayerServer();
        server.execute(() -> server.getCommands().performPrefixedCommand(server.createCommandSourceStack(), command));
    }

    private void screenshot(Minecraft mc, String name) {
        log("screenshot: " + name);
        shots.add(name);
        Screenshot.grab(mc.gameDirectory, Screens.renderTarget(mc), message -> {
        });
    }

    private void rememberShots(Minecraft mc) {
        File[] files = new File(mc.gameDirectory, "screenshots").listFiles();
        if (files != null) {
            for (File file : files) {
                existingShots.add(file.getName());
            }
        }
    }

    private void nameShots(Minecraft mc) {
        File dir = new File(mc.gameDirectory, "screenshots");
        File[] files = dir.listFiles((parent, name) -> name.endsWith(".png") && !existingShots.contains(name));
        if (files == null) {
            return;
        }
        List<File> fresh = new ArrayList<>(List.of(files));
        fresh.sort(Comparator.comparingLong(File::lastModified).thenComparing(File::getName));
        for (int i = 0; i < fresh.size() && i < shots.size(); i++) {
            File target = new File(dir, String.format(Locale.ROOT, "rw-%02d-%s.png", i, shots.get(i)));
            if ((!target.exists() || target.delete()) && fresh.get(i).renameTo(target)) {
                log("saved " + target.getName());
            }
        }
    }

    private static void deleteWorld(Minecraft mc, String name) {
        Path dir = mc.gameDirectory.toPath().resolve("saves").resolve(name);
        if (!Files.exists(dir)) {
            return;
        }
        try (Stream<Path> paths = Files.walk(dir)) {
            for (Path path : paths.sorted(Comparator.reverseOrder()).toList()) {
                Files.delete(path);
            }
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static void click(Minecraft mc, AbstractWidget widget) {
        Ui.click(Screens.current(mc), widget.getX() + widget.getWidth() / 2.0, widget.getY() + widget.getHeight() / 2.0);
    }

    private static AbstractWidget find(GuiEventListener node, String key) {
        if (node instanceof AbstractWidget widget && widget.getMessage().getContents() instanceof TranslatableContents contents
                && contents.getKey().equals(key)) {
            return widget;
        }
        if (node instanceof ContainerEventHandler container) {
            for (GuiEventListener child : container.children()) {
                AbstractWidget found = find(child, key);
                if (found != null) {
                    return found;
                }
            }
        }
        if (node instanceof Screen) {
            throw new AssertionError("no widget " + key + " on " + node);
        }
        return null;
    }

    static void check(boolean condition, String message) {
        if (!condition) {
            throw new AssertionError(message);
        }
    }

    static void log(String message) {
        System.out.println("[rewind-selftest] " + message);
    }
}
