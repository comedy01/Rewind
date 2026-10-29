package dev.rewind.client.gui;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

import java.util.List;

public final class Canvas {
    private final GuiGraphics graphics;
    private final Font font;

    public Canvas(GuiGraphics graphics, Font font) {
        this.graphics = graphics;
        this.font = font;
    }

    public void text(Component text, int x, int y, int color) {
        graphics.drawString(font, text, x, y, color);
    }

    public void text(String text, int x, int y, int color) {
        text(Component.literal(text), x, y, color);
    }

    public void centered(Component text, int x, int y, int color) {
        graphics.drawCenteredString(font, text, x, y, color);
    }

    public void fill(int x1, int y1, int x2, int y2, int color) {
        graphics.fill(x1, y1, x2, y2, color);
    }

    public void gradient(int x1, int y1, int x2, int y2, int top, int bottom) {
        graphics.fillGradient(x1, y1, x2, y2, top, bottom);
    }

    public void tooltip(List<Component> lines, int x, int y) {
        graphics.setComponentTooltipForNextFrame(font, lines, x, y);
    }
}
