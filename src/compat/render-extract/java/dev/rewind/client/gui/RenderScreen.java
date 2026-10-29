package dev.rewind.client.gui;

import net.minecraft.client.gui.GuiGraphicsExtractor;
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
    public void extractBackground(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        if (!drawBackground(new Canvas(graphics, font))) {
            super.extractBackground(graphics, mouseX, mouseY, partialTick);
        }
    }

    @Override
    public void extractRenderState(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float partialTick) {
        super.extractRenderState(graphics, mouseX, mouseY, partialTick);
        draw(new Canvas(graphics, font), mouseX, mouseY);
    }
}
