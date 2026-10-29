package dev.rewind.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.network.chat.Component;

public abstract class InputScreen extends RenderScreen {
    protected InputScreen(Component title) {
        super(title);
    }

    protected boolean press(double x, double y, int button) {
        return false;
    }

    protected boolean dragTo(double x, double y) {
        return false;
    }

    protected void release() {
    }

    protected boolean scroll(double amount) {
        return false;
    }

    protected boolean key(KeyPress press) {
        return false;
    }

    @Override
    public boolean mouseClicked(double x, double y, int button) {
        return super.mouseClicked(x, y, button) || press(x, y, button);
    }

    @Override
    public boolean mouseDragged(double x, double y, int button, double dx, double dy) {
        return dragTo(x, y) || super.mouseDragged(x, y, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double x, double y, int button) {
        release();
        return super.mouseReleased(x, y, button);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return scroll(scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(int key, int scanCode, int modifiers) {
        return key(new KeyPress(key, (modifiers & 1) != 0, InputConstants.getKey(key, scanCode)))
                || super.keyPressed(key, scanCode, modifiers);
    }
}
