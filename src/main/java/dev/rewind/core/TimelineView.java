package dev.rewind.core;

import java.util.Arrays;
import java.util.List;

public record TimelineView(int start, int now, int[] frames, String[] places, List<Marker> markers, long bytes) {
    public int length() {
        return Math.max(1, now - start);
    }

    public Marker lastDeath() {
        for (int i = markers.size() - 1; i >= 0; i--) {
            if (markers.get(i).kind() == Marker.Kind.DEATH) {
                return markers.get(i);
            }
        }
        return null;
    }

    public String placeAt(int tick) {
        int i = Arrays.binarySearch(frames, Frames.atOrBefore(frames, tick));
        return i < 0 ? null : places[i];
    }

    public String elsewhere(int tick) {
        String place = placeAt(tick);
        return place != null && !place.equals(placeAt(now)) ? place : null;
    }
}
