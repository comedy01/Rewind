package dev.rewind.client;

import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.minecraft.client.KeyMapping;

public final class KeyRegistrar {
    private KeyRegistrar() {
    }

    public static KeyMapping register(KeyMapping key) {
        KeyMapping.Category.register(key.getCategory().id());
        return KeyBindingHelper.registerKeyBinding(key);
    }
}
