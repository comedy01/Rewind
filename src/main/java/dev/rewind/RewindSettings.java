package dev.rewind;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.Properties;

public final class RewindSettings {
    public static final int[] HISTORY_LIMITS_MB = {0, 128, 256, 512, 768, 1024, 1536, 2048, 3072, 4096};
    public static final int MIN_JUMP_SECONDS = 1;
    public static final int MAX_JUMP_SECONDS = 10;

    private static volatile Path file = Path.of("config", "rewind.properties");
    private static volatile boolean loaded;
    private static volatile int historyLimitMb;
    private static volatile int jumpBeforeSeconds = 3;
    private static volatile boolean deathButton = true;

    private RewindSettings() {
    }

    public static void setFile(Path path) {
        file = path;
        loaded = false;
    }

    public static int historyLimitMb() {
        load();
        return historyLimitMb;
    }

    public static void setHistoryLimitMb(int mb) {
        load();
        historyLimitMb = Math.max(0, mb);
    }

    public static long historyBudget() {
        int mb = historyLimitMb();
        return mb == 0 ? autoHistoryBudget() : Math.min((long) mb << 20, maxHistoryBudget());
    }

    private static long maxHistoryBudget() {
        return Runtime.getRuntime().maxMemory() * 3 / 4;
    }

    public static long autoHistoryBudget() {
        return Math.min(768L << 20, Runtime.getRuntime().maxMemory() / 4);
    }

    public static int[] availableHistoryLimits() {
        long max = maxHistoryBudget();
        return Arrays.stream(HISTORY_LIMITS_MB).filter(mb -> ((long) mb << 20) <= max).toArray();
    }

    public static int jumpBeforeSeconds() {
        load();
        return jumpBeforeSeconds;
    }

    public static int jumpBeforeTicks() {
        return jumpBeforeSeconds() * 20;
    }

    public static void setJumpBeforeSeconds(int seconds) {
        load();
        jumpBeforeSeconds = clampJump(seconds);
    }

    public static boolean deathButton() {
        load();
        return deathButton;
    }

    public static void setDeathButton(boolean enabled) {
        load();
        deathButton = enabled;
    }

    private static synchronized void load() {
        if (loaded) {
            return;
        }
        loaded = true;
        Path path = file;
        if (!Files.isRegularFile(path)) {
            return;
        }
        Properties props = new Properties();
        try (Reader reader = Files.newBufferedReader(path)) {
            props.load(reader);
        } catch (IOException e) {
            Rewind.LOGGER.warn("Could not read {}", path, e);
            return;
        }
        historyLimitMb = Math.max(0, parse(props, "historyLimitMb", 0));
        jumpBeforeSeconds = clampJump(parse(props, "jumpBeforeSeconds", 3));
        deathButton = Boolean.parseBoolean(props.getProperty("deathButton", "true"));
    }

    public static synchronized void save() {
        load();
        Properties props = new Properties();
        props.setProperty("historyLimitMb", Integer.toString(historyLimitMb));
        props.setProperty("jumpBeforeSeconds", Integer.toString(jumpBeforeSeconds));
        props.setProperty("deathButton", Boolean.toString(deathButton));
        Path path = file;
        try {
            if (path.getParent() != null) {
                Files.createDirectories(path.getParent());
            }
            try (Writer writer = Files.newBufferedWriter(path)) {
                props.store(writer, "Rewind settings");
            }
        } catch (IOException e) {
            Rewind.LOGGER.warn("Could not save {}", path, e);
        }
    }

    private static int clampJump(int seconds) {
        return Math.max(MIN_JUMP_SECONDS, Math.min(MAX_JUMP_SECONDS, seconds));
    }

    private static int parse(Properties props, String key, int fallback) {
        try {
            return Integer.parseInt(props.getProperty(key, Integer.toString(fallback)).trim());
        } catch (NumberFormatException e) {
            return fallback;
        }
    }
}
