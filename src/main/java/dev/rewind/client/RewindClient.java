package dev.rewind.client;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rewind.Rewind;
import dev.rewind.client.gui.RewindScreen;
import dev.rewind.client.gui.ScreenOpener;
import dev.rewind.server.Recorder;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.server.IntegratedServer;
import net.minecraft.network.chat.Component;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Consumer;

public final class RewindClient {
    public static final String MOD_ID = Rewind.MOD_ID;
    private static final int RESPAWN_PATIENCE = 200;

    private static final AtomicBoolean previewQueued = new AtomicBoolean();
    private static volatile int requested;
    private static KeyMapping key;
    private static LocalPlayer respawningFrom;
    private static int respawnWait;

    private RewindClient() {
    }

    public static KeyMapping createKey() {
        return KeyFactory.create("key.rewind.open", InputConstants.KEY_R);
    }

    public static KeyMapping key() {
        return key;
    }

    public static void setKey(KeyMapping mapping) {
        key = mapping;
    }

    public static void tick(Minecraft mc) {
        if (key != null) {
            while (key.consumeClick()) {
                open(mc, false);
            }
        }
        if (respawningFrom != null) {
            Recorder recorder = recorder(mc);
            boolean ready = recorder != null && recorder.settled() || respawnWait > RESPAWN_PATIENCE / 2;
            if (mc.player != null && mc.player != respawningFrom && mc.player.isAlive() && ready) {
                respawningFrom = null;
                ScreenOpener.open(mc, new RewindScreen(true));
            } else if (++respawnWait > RESPAWN_PATIENCE) {
                respawningFrom = null;
                run(Recorder::stopWatchingSettling);
            }
        }
    }

    public static boolean available(Minecraft mc) {
        Recorder recorder = recorder(mc);
        return recorder != null && recorder.hasHistory();
    }

    public static void open(Minecraft mc, boolean afterDeath) {
        if (mc.player == null || mc.level == null || !mc.player.isAlive()) {
            return;
        }
        if (!available(mc)) {
            Overlay.show(mc, Component.translatable(mc.hasSingleplayerServer()
                    ? "rewind.message.no_history" : "rewind.message.singleplayer"));
            return;
        }
        ScreenOpener.open(mc, new RewindScreen(afterDeath));
    }

    public static void rewindAfterDeath(Minecraft mc) {
        if (mc.player == null) {
            return;
        }
        respawningFrom = mc.player;
        respawnWait = 0;
        run(Recorder::watchSettling);
        mc.player.respawn();
    }

    public static void begin() {
        run(Recorder::begin);
    }

    public static void previewTo(int tick) {
        requested = tick;
        if (previewQueued.compareAndSet(false, true)) {
            run(recorder -> {
                previewQueued.set(false);
                recorder.previewTo(requested);
            });
        }
    }

    public static void commit(int tick) {
        run(recorder -> recorder.commit(tick));
    }

    public static void cancel() {
        run(Recorder::cancel);
    }

    public static Recorder recorder(Minecraft mc) {
        IntegratedServer server = mc.getSingleplayerServer();
        Recorder recorder = Recorder.current();
        if (server == null || server.isPublished() || recorder == null || recorder.server() != server) {
            return null;
        }
        return recorder;
    }

    private static void run(Consumer<Recorder> action) {
        Recorder recorder = recorder(Minecraft.getInstance());
        if (recorder != null) {
            recorder.server().execute(() -> action.accept(recorder));
        }
    }
}
