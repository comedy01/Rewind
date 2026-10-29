package dev.rewind.selftest;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.KeyEvent;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.client.input.MouseButtonInfo;

final class Ui {
    private Ui() {
    }

    static void click(Screen screen, double x, double y) {
        MouseButtonEvent event = new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(event, false);
        screen.mouseReleased(event);
    }

    static void drag(Screen screen, double fromX, double toX, double y) {
        MouseButtonEvent down = new MouseButtonEvent(fromX, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0));
        screen.mouseClicked(down, false);
        int steps = 8;
        for (int i = 1; i <= steps; i++) {
            double x = fromX + (toX - fromX) * i / steps;
            screen.mouseDragged(new MouseButtonEvent(x, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)),
                    (toX - fromX) / steps, 0);
        }
        screen.mouseReleased(new MouseButtonEvent(toX, y, new MouseButtonInfo(InputConstants.MOUSE_BUTTON_LEFT, 0)));
    }

    static void key(Screen screen, int key) {
        screen.keyPressed(new KeyEvent(key, 0, 0));
    }
}
