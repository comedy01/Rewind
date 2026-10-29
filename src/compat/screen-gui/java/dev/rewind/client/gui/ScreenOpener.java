package dev.rewind.client.gui;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Screen;

public final class ScreenOpener {
    private ScreenOpener() {
    }

    public static void open(Minecraft client, Screen screen) {
        client.gui.setScreen(screen);
    }

    public static Screen current(Minecraft client) {
        return client.gui.screen();
    }
}
