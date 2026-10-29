package dev.rewind.server;

import net.minecraft.resources.ResourceKey;

public final class Ids {
    private Ids() {
    }

    public static String of(ResourceKey<?> key) {
        return key.identifier().toString();
    }
}
