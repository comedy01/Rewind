package dev.rewind.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.ResourceLocation;

final class KeyFactory {
    private static KeyMapping.Category category;

    private KeyFactory() {
    }

    static KeyMapping create(String name, int code) {
        if (category == null) {
            category = KeyMapping.Category.register(ResourceLocation.fromNamespaceAndPath(RewindClient.MOD_ID, "main"));
        }
        return new KeyMapping(name, code, category);
    }
}
