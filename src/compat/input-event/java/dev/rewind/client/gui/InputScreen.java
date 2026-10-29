package dev.rewind.client.gui;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
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
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        return super.mouseClicked(event, doubleClick) || press(event.x(), event.y(), event.button());
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dx, double dy) {
        return dragTo(event.x(), event.y()) || super.mouseDragged(event, dx, dy);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        release();
        return super.mouseReleased(event);
    }

    @Override
    public boolean mouseScrolled(double x, double y, double scrollX, double scrollY) {
        return scroll(scrollY) || super.mouseScrolled(x, y, scrollX, scrollY);
    }

    @Override
    public boolean keyPressed(KeyEvent event) {
        return key(new KeyPress(event.key(), event.hasShiftDown(), InputConstants.getKey(event))) || super.keyPressed(event);
    }
}
