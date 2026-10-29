package dev.rewind.client;

import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

final class Overlay {
    private Overlay() {
    }

    static void show(Minecraft mc, Component message) {
        mc.player.sendOverlayMessage(message);
    }
}
