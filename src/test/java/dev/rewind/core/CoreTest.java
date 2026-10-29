package dev.rewind.core;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CoreTest {
    @Test
    void timeText() {
        assertEquals("Now", TimeText.ago(0));
        assertEquals("−0:01", TimeText.ago(1));
        assertEquals("−0:01", TimeText.ago(20));
        assertEquals("−3:42", TimeText.ago(222 * 20));
        assertEquals("−1:02:05", TimeText.ago(3725 * 20));
    }

    @Test
    void recentFramesAreAllKept() {
        int[] ticks = {0, 5, 10, 15, 20, 25};
        boolean[] keep = Retention.keep(ticks, 30);
        for (boolean k : keep) {
            assertTrue(k);
        }
    }

    @Test
    void oldFramesAreThinnedToOnePerBucket() {
        int now = Retention.MIDDLE + 1000;
        List<Integer> ticks = new ArrayList<>();
        for (int t = 0; t <= now; t += Retention.FRAME_STEP) {
            ticks.add(t);
        }
        int[] all = ticks.stream().mapToInt(Integer::intValue).toArray();
        boolean[] keep = Retention.keep(all, now);
        int keptOld = 0;
        int keptMiddle = 0;
        int keptRecent = 0;
        for (int i = 0; i < all.length; i++) {
            if (!keep[i]) {
                continue;
            }
            int age = now - all[i];
            if (age >= Retention.MIDDLE) {
                keptOld++;
            } else if (age >= Retention.RECENT) {
                keptMiddle++;
            } else {
                keptRecent++;
            }
        }
        assertEquals(Retention.RECENT / Retention.FRAME_STEP, keptRecent, 1);
        assertEquals((Retention.MIDDLE - Retention.RECENT) / Retention.MIDDLE_STEP, keptMiddle, 2);
        assertEquals(1000 / Retention.OLD_STEP, keptOld, 2);
        assertTrue(keep[0]);
        assertTrue(keep[all.length - 1]);

        List<Integer> kept = new ArrayList<>();
        for (int i = 0; i < all.length; i++) {
            if (keep[i]) {
                kept.add(all[i]);
            }
        }
        int[] once = kept.stream().mapToInt(Integer::intValue).toArray();
        boolean[] again = Retention.keep(once, now);
        for (boolean k : again) {
            assertTrue(k);
        }
    }

    @Test
    void frameLookups() {
        int[] ticks = {0, 5, 10, 30};
        assertEquals(5, Frames.nearest(ticks, 6));
        assertEquals(10, Frames.nearest(ticks, 9));
        assertEquals(30, Frames.nearest(ticks, 50));
        assertEquals(0, Frames.nearest(ticks, -3));
        assertEquals(10, Frames.atOrBefore(ticks, 29));
        assertEquals(30, Frames.atOrBefore(ticks, 30));
        assertEquals(0, Frames.atOrBefore(ticks, -1));
        assertEquals(5, Frames.step(ticks, 10, -1));
        assertEquals(30, Frames.step(ticks, 10, 1));
        assertEquals(30, Frames.step(ticks, 30, 1));
        assertEquals(10, Frames.step(ticks, 20, -1));
        assertEquals(30, Frames.step(ticks, 20, 1));
        assertArrayEquals(new int[] {0, 5, 10, 30}, ticks);
    }

    @Test
    void lastDeathIsTheNewest() {
        TimelineView view = new TimelineView(0, 100, new int[] {0, 100}, new String[2], List.of(
                new Marker(Marker.Kind.DEATH, 10, "a"),
                new Marker(Marker.Kind.EXPLOSION, 50, "b"),
                new Marker(Marker.Kind.DEATH, 70, "c")), 0);
        assertEquals("c", view.lastDeath().label());
    }

    @Test
    void elsewhereIsWhereRewindingTakesYou() {
        String over = "minecraft:overworld";
        String nether = "minecraft:the_nether";
        TimelineView view = new TimelineView(0, 40, new int[] {0, 10, 20, 30, 40},
                new String[] {over, nether, nether, null, over}, List.of(), 0);
        assertNull(view.elsewhere(0));
        assertEquals(nether, view.elsewhere(10));
        assertEquals(nether, view.elsewhere(25));
        assertNull(view.elsewhere(30));
        assertNull(view.elsewhere(40));
        assertEquals(over, view.placeAt(5));
    }

    @Test
    void placeNames() {
        assertEquals("the Nether", Places.name("minecraft:the_nether"));
        assertEquals("Twilight Forest", Places.name("twilightforest:twilight_forest"));
    }
}
