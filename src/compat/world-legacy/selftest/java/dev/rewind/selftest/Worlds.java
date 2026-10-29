package dev.rewind.selftest;

import net.minecraft.client.Minecraft;
import net.minecraft.core.registries.Registries;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.Difficulty;
import net.minecraft.world.level.GameRules;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.LevelSettings;
import net.minecraft.world.level.WorldDataConfiguration;
import net.minecraft.world.level.levelgen.WorldOptions;
import net.minecraft.world.level.levelgen.presets.WorldPresets;

final class Worlds {
    private Worlds() {
    }

    static void create(Minecraft mc, String name, boolean flat, long seed) {
        LevelSettings settings = new LevelSettings(name, GameType.SURVIVAL, false, Difficulty.EASY, true,
                new GameRules(), WorldDataConfiguration.DEFAULT);
        mc.createWorldOpenFlows().createFreshLevel(name, settings, new WorldOptions(seed, !flat, false),
                registries -> registries.registryOrThrow(Registries.WORLD_PRESET)
                        .getHolderOrThrow(flat ? WorldPresets.FLAT : WorldPresets.NORMAL)
                        .value().createWorldDimensions());
    }

    static long time(ServerLevel level) {
        return level.getDayTime();
    }

    static String noMobSpawning() {
        return "gamerule doMobSpawning false";
    }
}
