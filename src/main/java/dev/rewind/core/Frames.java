package dev.rewind.core;

import java.util.Arrays;

public final class Frames {
    private Frames() {
    }

    public static int nearest(int[] ticks, int tick) {
        if (ticks.length == 0) {
            return tick;
        }
        int i = Arrays.binarySearch(ticks, tick);
        if (i >= 0) {
            return ticks[i];
        }
        int after = -i - 1;
        if (after == 0) {
            return ticks[0];
        }
        if (after >= ticks.length) {
            return ticks[ticks.length - 1];
        }
        int before = ticks[after - 1];
        return tick - before <= ticks[after] - tick ? before : ticks[after];
    }

    public static int atOrBefore(int[] ticks, int tick) {
        int i = Arrays.binarySearch(ticks, tick);
        if (i >= 0) {
            return ticks[i];
        }
        int after = -i - 1;
        return after == 0 ? (ticks.length == 0 ? tick : ticks[0]) : ticks[after - 1];
    }

    public static int step(int[] ticks, int tick, int direction) {
        int i = Arrays.binarySearch(ticks, tick);
        if (i < 0) {
            int after = -i - 1;
            i = direction < 0 ? after : after - 1;
        }
        int next = Math.max(0, Math.min(ticks.length - 1, i + direction));
        return ticks.length == 0 ? tick : ticks[next];
    }
}
