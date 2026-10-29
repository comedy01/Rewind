package dev.rewind.client.gui;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;

public abstract class RenderScreen extends Screen {
    protected RenderScreen(Component title) {
        super(title);
    }

    protected boolean drawBackground(Canvas canvas) {
        return false;
    }

    protected void draw(Canvas canvas, int mouseX, int mouseY) {
    }

    @Override
    public void render(GuiGraphics graphics, int mouseX, int mouseY, float partialTick) {
        Canvas canvas = new Canvas(graphics, font);
        if (!drawBackground(canvas)) {
            renderBackground(graphics);
        }
        super.render(graphics, mouseX, mouseY, partialTick);
        draw(canvas, mouseX, mouseY);
    }
}
