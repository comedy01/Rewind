package dev.rewind.server;

import net.minecraft.network.protocol.game.ClientboundSetExperiencePacket;
import net.minecraft.network.protocol.game.ClientboundSetHealthPacket;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.Container;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

record PlayerState(ResourceKey<Level> dimension, double x, double y, double z, float yRot, float xRot,
                   float health, float absorption, int food, float saturation,
                   int xpLevel, float xpProgress, int xpTotal,
                   ItemStack[] inventory, int selected, ItemStack[] enderChest,
                   List<MobEffectInstance> effects, int fireTicks, int air, GameType gameMode) {

    static PlayerState capture(ServerPlayer player, PlayerState previous) {
        List<MobEffectInstance> effects = new ArrayList<>();
        for (MobEffectInstance effect : player.getActiveEffects()) {
            effects.add(new MobEffectInstance(effect));
        }
        Inventory inventory = player.getInventory();
        return new PlayerState(player.level().dimension(), player.getX(), player.getY(), player.getZ(),
                player.getYRot(), player.getXRot(), player.getHealth(), player.getAbsorptionAmount(),
                player.getFoodData().getFoodLevel(), player.getFoodData().getSaturationLevel(),
                player.experienceLevel, player.experienceProgress, player.totalExperience,
                copy(inventory, previous == null ? null : previous.inventory()), Players.selected(inventory),
                copy(player.getEnderChestInventory(), previous == null ? null : previous.enderChest()),
                List.copyOf(effects), player.getRemainingFireTicks(), player.getAirSupply(), player.gameMode.getGameModeForPlayer());
    }

    void restore(MinecraftServer server, ServerPlayer player, boolean changeDimension) {
        ServerLevel level = server.getLevel(dimension);
        if (level == null) {
            level = server.overworld();
        }
        if (changeDimension || level == player.level()) {
            player.stopRiding();
            Players.teleport(player, level, x, y, z, yRot, xRot);
            player.setDeltaMovement(Vec3.ZERO);
            player.resetFallDistance();
        }

        player.setGameMode(gameMode);
        player.setHealth(health);
        player.setAbsorptionAmount(absorption);
        player.getFoodData().setFoodLevel(food);
        player.getFoodData().setSaturation(saturation);
        player.experienceLevel = xpLevel;
        player.experienceProgress = xpProgress;
        player.totalExperience = xpTotal;
        Inventory inv = player.getInventory();
        for (int i = 0; i < inventory.length && i < inv.getContainerSize(); i++) {
            inv.setItem(i, inventory[i].copy());
        }
        Container ender = player.getEnderChestInventory();
        for (int i = 0; i < enderChest.length && i < ender.getContainerSize(); i++) {
            ender.setItem(i, enderChest[i].copy());
        }
        player.removeAllEffects();
        for (MobEffectInstance effect : effects) {
            player.addEffect(new MobEffectInstance(effect));
        }
        player.setRemainingFireTicks(fireTicks);
        player.setAirSupply(air);

        player.connection.send(new ClientboundSetHealthPacket(health, food, saturation));
        player.connection.send(new ClientboundSetExperiencePacket(xpProgress, xpTotal, xpLevel));
        Players.select(player, selected);
        player.inventoryMenu.sendAllDataToRemote();
        player.containerMenu.sendAllDataToRemote();
    }

    private static ItemStack[] copy(Container container, ItemStack[] previous) {
        int size = container.getContainerSize();
        boolean same = previous != null && previous.length == size;
        for (int i = 0; same && i < size; i++) {
            same = ItemStack.matches(previous[i], container.getItem(i));
        }
        if (same) {
            return previous;
        }
        ItemStack[] items = new ItemStack[size];
        for (int i = 0; i < size; i++) {
            items[i] = container.getItem(i).copy();
        }
        return items;
    }
}
