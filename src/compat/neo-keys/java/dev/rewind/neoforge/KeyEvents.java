package dev.rewind.neoforge;

import net.minecraft.client.KeyMapping;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;

final class KeyEvents {
    private KeyEvents() {
    }

    static void register(RegisterKeyMappingsEvent event, KeyMapping key) {
        event.register(key);
    }
}
