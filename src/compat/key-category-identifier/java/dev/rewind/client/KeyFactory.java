package dev.rewind.client;

import net.minecraft.client.KeyMapping;
import net.minecraft.resources.Identifier;

final class KeyFactory {
    private static final KeyMapping.Category CATEGORY =
            new KeyMapping.Category(Identifier.fromNamespaceAndPath(RewindClient.MOD_ID, "main"));

    private KeyFactory() {
    }

    static KeyMapping create(String name, int code) {
        return new KeyMapping(name, code, CATEGORY);
    }
}
