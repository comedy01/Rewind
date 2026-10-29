package dev.rewind.selftest;

import net.minecraft.client.gui.screens.Screen;

final class Ui {
    private Ui() {
    }

    static void click(Screen screen, double x, double y) {
        screen.mouseClicked(x, y, 0);
        screen.mouseReleased(x, y, 0);
    }

    static void drag(Screen screen, double fromX, double toX, double y) {
        screen.mouseClicked(fromX, y, 0);
        int steps = 8;
        for (int i = 1; i <= steps; i++) {
            double x = fromX + (toX - fromX) * i / steps;
            screen.mouseDragged(x, y, 0, (toX - fromX) / steps, 0);
        }
        screen.mouseReleased(toX, y, 0);
    }

    static void key(Screen screen, int key) {
        screen.keyPressed(key, 0, 0);
    }
}
