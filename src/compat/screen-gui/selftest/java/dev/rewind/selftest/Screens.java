package dev.rewind.selftest;

import com.mojang.blaze3d.pipeline.RenderTarget;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.Overlay;
import net.minecraft.client.gui.screens.Screen;

final class Screens {
    private Screens() {
    }

    static Screen current(Minecraft mc) {
        return mc.gui.screen();
    }

    static void open(Minecraft mc, Screen screen) {
        mc.gui.setScreen(screen);
    }

    static Overlay overlay(Minecraft mc) {
        return mc.gui.overlay();
    }

    static RenderTarget renderTarget(Minecraft mc) {
        return mc.gameRenderer.mainRenderTarget();
    }
}
