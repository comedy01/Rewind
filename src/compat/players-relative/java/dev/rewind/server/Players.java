package dev.rewind.server;

import net.minecraft.network.protocol.game.ClientboundSetHeldSlotPacket;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Inventory;

import java.util.Set;

final class Players {
    private Players() {
    }

    static int selected(Inventory inventory) {
        return inventory.selected;
    }

    static void select(ServerPlayer player, int slot) {
        player.getInventory().selected = slot;
        player.connection.send(new ClientboundSetHeldSlotPacket(slot));
    }

    static void teleport(ServerPlayer player, ServerLevel level, double x, double y, double z, float yRot, float xRot) {
        player.teleportTo(level, x, y, z, Set.of(), yRot, xRot, true);
    }
}
