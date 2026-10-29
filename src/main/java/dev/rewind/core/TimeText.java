package dev.rewind.core;

import java.util.Locale;

public final class TimeText {
    private TimeText() {
    }

    public static String ago(int ticks) {
        if (ticks <= 0) {
            return "Now";
        }
        int seconds = (ticks + 19) / 20;
        int hours = seconds / 3600;
        int minutes = seconds / 60 % 60;
        int secs = seconds % 60;
        if (hours > 0) {
            return String.format(Locale.ROOT, "−%d:%02d:%02d", hours, minutes, secs);
        }
        return String.format(Locale.ROOT, "−%d:%02d", minutes, secs);
    }
}
