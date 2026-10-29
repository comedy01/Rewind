package dev.rewind.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import dev.rewind.RewindSettings;
import dev.rewind.client.RewindClient;
import dev.rewind.core.Frames;
import dev.rewind.core.Marker;
import dev.rewind.core.Places;
import dev.rewind.core.TimeText;
import dev.rewind.core.TimelineView;
import dev.rewind.server.Recorder;
import net.minecraft.client.gui.components.Button;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class RewindScreen extends InputScreen {
    private static final int SIDE = 24;
    private static final int PAST = 0xFF4FA8FF;
    private static final int FUTURE = 0x66FFFFFF;
    private static final int TRACK = 0x99000000;
    private static final int MARKER_ROW = 9;
    private static final int MARKER_ROWS = 4;

    private final boolean afterDeath;
    private TimelineView view;
    private int target = -1;
    private boolean dragging;
    private boolean finished;
    private Button commit;

    public RewindScreen(boolean afterDeath) {
        super(Component.translatable("rewind.screen.title"));
        this.afterDeath = afterDeath;
    }

    @Override
    protected void init() {
        commit = addRenderableWidget(Button.builder(Component.translatable("rewind.screen.commit"), b -> commit())
                .bounds(width - 8 - 70 - 4 - 96, 8, 96, 20).build());
        addRenderableWidget(Button.builder(Component.translatable("gui.cancel"), b -> onClose())
                .bounds(width - 8 - 70, 8, 70, 20).build());
        RewindClient.begin();
        refresh();
    }

    @Override
    public void tick() {
        refresh();
    }

    private void refresh() {
        if (view == null) {
            Recorder recorder = RewindClient.recorder(minecraft);
            view = recorder == null ? null : recorder.view();
            if (view != null) {
                target = view.now();
                Marker death = afterDeath ? view.lastDeath() : null;
                if (death != null) {
                    selectBefore(death.tick() - RewindSettings.jumpBeforeTicks());
                }
            }
        }
        if (commit != null) {
            commit.active = view != null && target < view.now();
        }
    }

    public void select(int tick) {
        if (view == null) {
            return;
        }
        int snapped = Frames.nearest(view.frames(), Math.max(view.start(), Math.min(view.now(), tick)));
        if (snapped != target) {
            target = snapped;
            RewindClient.previewTo(target);
        }
    }

    public void selectBefore(int tick) {
        if (view != null) {
            select(Frames.atOrBefore(view.frames(), Math.max(view.start(), tick)));
        }
    }

    private void commit() {
        if (view == null || target >= view.now()) {
            return;
        }
        finished = true;
        RewindClient.commit(target);
        super.onClose();
    }

    @Override
    public void onClose() {
        cancelOnce();
        super.onClose();
    }

    @Override
    public void removed() {
        cancelOnce();
    }

    private void cancelOnce() {
        if (!finished) {
            finished = true;
            RewindClient.cancel();
        }
    }

    @Override
    protected boolean press(double x, double y, int button) {
        if (view == null || button != InputConstants.MOUSE_BUTTON_LEFT) {
            return false;
        }
        Placed marker = markerAt(x, y);
        if (marker != null) {
            selectBefore(marker.marker().tick() - RewindSettings.jumpBeforeTicks());
            return true;
        }
        if (onTrack(y)) {
            dragging = true;
            select(tickAt(x));
            return true;
        }
        return false;
    }

    @Override
    protected boolean dragTo(double x, double y) {
        if (dragging) {
            select(tickAt(x));
            return true;
        }
        return false;
    }

    @Override
    protected void release() {
        dragging = false;
    }

    @Override
    protected boolean scroll(double amount) {
        if (view != null && amount != 0) {
            select(Frames.step(view.frames(), target, amount > 0 ? -1 : 1));
            return true;
        }
        return false;
    }

    @Override
    protected boolean key(KeyPress press) {
        if (view != null) {
            boolean shift = press.shift();
            switch (press.key()) {
                case InputConstants.KEY_LEFT -> {
                    select(shift ? target - 100 : Frames.step(view.frames(), target, -1));
                    return true;
                }
                case InputConstants.KEY_RIGHT -> {
                    select(shift ? target + 100 : Frames.step(view.frames(), target, 1));
                    return true;
                }
                case InputConstants.KEY_HOME -> {
                    select(view.start());
                    return true;
                }
                case InputConstants.KEY_END -> {
                    select(view.now());
                    return true;
                }
                case InputConstants.KEY_RETURN, InputConstants.KEY_NUMPADENTER -> {
                    commit();
                    return true;
                }
                default -> {
                }
            }
        }
        return false;
    }

    @Override
    protected boolean drawBackground(Canvas graphics) {
        graphics.gradient(0, 0, width, 44, 0xB0000000, 0x00000000);
        graphics.gradient(0, trackY() - 44, width, trackY() + 24, 0x00000000, 0xA0000000);
        graphics.gradient(0, trackY() + 24, width, height, 0xA0000000, 0x00000000);
        return true;
    }

    @Override
    protected void draw(Canvas graphics, int mouseX, int mouseY) {
        graphics.text(title, 10, 8, 0xFFFFFFFF);
        graphics.text(Component.translatable("rewind.screen.hint"), 10, 20, 0xFFC8C8C8);
        graphics.text(Component.translatable("rewind.screen.keys"), 10, 30, 0xFF9A9A9A);
        if (view == null) {
            graphics.centered(Component.translatable("rewind.screen.loading"), width / 2, trackY() - 4, 0xFFFFFFFF);
            return;
        }
        int y = trackY();
        int left = SIDE;
        int right = width - SIDE;
        int handle = trackX(target);
        graphics.fill(left - 1, y - 1, right + 1, y + 5, TRACK);
        graphics.fill(left, y, handle, y + 4, PAST);
        graphics.fill(handle, y, right, y + 4, FUTURE);
        drawElsewhere(graphics, y + 5);

        Placed hovered = markerAt(mouseX, mouseY);
        for (Placed placed : placeMarkers()) {
            int size = hovered != null && placed.marker() == hovered.marker() ? 4 : 3;
            int color = placed.marker().kind().color();
            graphics.fill(placed.x(), placed.y() + size, placed.x() + 1, y, color);
            graphics.fill(placed.x() - size - 1, placed.y() - size - 1, placed.x() + size + 2, placed.y() + size + 2, 0xFF000000);
            graphics.fill(placed.x() - size, placed.y() - size, placed.x() + size + 1, placed.y() + size + 1, color);
        }

        graphics.fill(handle - 2, y - 6, handle + 3, y + 10, 0xFF000000);
        graphics.fill(handle - 1, y - 5, handle + 2, y + 9, 0xFFFFFFFF);
        String label = TimeText.ago(view.now() - target);
        int labelWidth = font.width(label);
        int labelX = Math.max(left, Math.min(right - labelWidth, handle - labelWidth / 2));
        int labelY = y - 16 - MARKER_ROW * markerRows();
        graphics.fill(labelX - 3, labelY - 2, labelX + labelWidth + 3, labelY + 10, 0xC0000000);
        graphics.text(label, labelX, labelY, 0xFFFFFFFF);

        graphics.text(TimeText.ago(view.length()), left, y + 10, 0xFFB8B8B8);
        String now = TimeText.ago(0);
        graphics.text(now, right - font.width(now), y + 10, 0xFFB8B8B8);
        graphics.centered(Component.literal(status()), width / 2, y + 10, 0xFFDDDDDD);

        if (hovered != null) {
            Marker marker = hovered.marker();
            graphics.tooltip(List.of(
                    colored(Component.literal(marker.label()), marker.kind().color()),
                    Component.translatable("rewind.screen.marker_hint", TimeText.ago(view.now() - marker.tick()))),
                    mouseX, mouseY);
        } else if (onTrack(mouseY) && mouseX >= left && mouseX <= right) {
            int tick = tickAt(mouseX);
            String elsewhere = view.elsewhere(tick);
            Component time = Component.literal(TimeText.ago(view.now() - tick));
            graphics.tooltip(elsewhere == null ? List.of(time) : List.of(time,
                    colored(Component.translatable("rewind.screen.in_place", Places.name(elsewhere)), Places.color(elsewhere))),
                    mouseX, mouseY);
        }
    }

    private void drawElsewhere(Canvas graphics, int y) {
        int[] ticks = view.frames();
        int i = 0;
        while (i < ticks.length) {
            String place = view.elsewhere(ticks[i]);
            int j = i + 1;
            while (j < ticks.length && Objects.equals(view.elsewhere(ticks[j]), place)) {
                j++;
            }
            if (place != null) {
                int end = j < ticks.length ? ticks[j] : view.now();
                graphics.fill(trackX(ticks[i]), y, Math.max(trackX(end), trackX(ticks[i]) + 1), y + 2, Places.color(place));
            }
            i = j;
        }
    }

    private static Component colored(MutableComponent text, int color) {
        return text.withStyle(style -> style.withColor(color & 0xFFFFFF));
    }

    private record Placed(Marker marker, int x, int y) {
    }

    private List<Placed> placeMarkers() {
        List<Placed> placed = new ArrayList<>();
        int row = 0;
        int lastX = Integer.MIN_VALUE;
        for (Marker marker : view.markers()) {
            int x = trackX(marker.tick());
            row = !placed.isEmpty() && x - lastX < 9 ? Math.min(row + 1, MARKER_ROWS - 1) : 0;
            lastX = x;
            placed.add(new Placed(marker, x, trackY() - 10 - row * MARKER_ROW));
        }
        return placed;
    }

    public int markerRows() {
        int rows = 0;
        for (Placed placed : placeMarkers()) {
            rows = Math.max(rows, (trackY() - 10 - placed.y()) / MARKER_ROW + 1);
        }
        return rows;
    }

    private String status() {
        Recorder recorder = RewindClient.recorder(minecraft);
        if (recorder != null && recorder.shownCursor() != target) {
            return Component.translatable("rewind.screen.moving").getString();
        }
        if (target >= view.now()) {
            return Component.translatable("rewind.screen.present").getString();
        }
        String elsewhere = view.elsewhere(target);
        if (elsewhere != null) {
            return Component.translatable("rewind.screen.elsewhere", Places.name(elsewhere)).getString();
        }
        return Component.translatable("rewind.screen.history", TimeText.ago(view.length()).substring(1)).getString();
    }

    public int trackY() {
        return height - 70;
    }

    public int trackX(int tick) {
        if (view == null) {
            return SIDE;
        }
        return SIDE + (int) Math.round((double) (tick - view.start()) * (width - 2 * SIDE) / view.length());
    }

    public int tickAt(double x) {
        return view.start() + (int) Math.round((x - SIDE) * view.length() / (double) (width - 2 * SIDE));
    }

    public int[] markerPoint(Marker marker) {
        for (Placed placed : placeMarkers()) {
            if (placed.marker() == marker) {
                return new int[] {placed.x(), placed.y()};
            }
        }
        return null;
    }

    public int target() {
        return target;
    }

    public TimelineView view() {
        return view;
    }

    private boolean onTrack(double y) {
        return y >= trackY() - 8 && y <= trackY() + 12;
    }

    private Placed markerAt(double x, double y) {
        if (view == null) {
            return null;
        }
        Placed best = null;
        double bestDistance = 6;
        for (Placed placed : placeMarkers()) {
            double distance = Math.max(Math.abs(placed.x() - x), Math.abs(placed.y() - y));
            if (distance <= bestDistance) {
                best = placed;
                bestDistance = distance;
            }
        }
        return best;
    }

    @Override
    public boolean isPauseScreen() {
        return true;
    }
}
