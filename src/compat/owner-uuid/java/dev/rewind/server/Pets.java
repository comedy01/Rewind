package dev.rewind.server;

import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.OwnableEntity;

public final class Pets {
    private Pets() {
    }

    public static boolean owned(LivingEntity entity) {
        return entity instanceof OwnableEntity ownable && ownable.getOwnerUUID() != null;
    }
}
