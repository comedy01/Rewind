package dev.rewind.core;

public final class Retention {
    public static final int FRAME_STEP = 5;
    public static final int RECENT = 20 * 60;
    public static final int MIDDLE = 20 * 60 * 10;
    public static final int MIDDLE_STEP = 20;
    public static final int OLD_STEP = 100;

    private Retention() {
    }

    public static int spacing(int age) {
        if (age < RECENT) {
            return FRAME_STEP;
        }
        return age < MIDDLE ? MIDDLE_STEP : OLD_STEP;
    }

    public static boolean[] keep(int[] ticks, int now) {
        boolean[] keep = new boolean[ticks.length];
        int lastSpacing = -1;
        int lastBucket = 0;
        for (int i = 0; i < ticks.length; i++) {
            int spacing = spacing(now - ticks[i]);
            int bucket = Math.floorDiv(ticks[i], spacing);
            keep[i] = i == 0 || i == ticks.length - 1 || spacing != lastSpacing || bucket != lastBucket;
            lastSpacing = spacing;
            lastBucket = bucket;
        }
        return keep;
    }
}
