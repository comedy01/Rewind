package dev.rewind.server;

import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.storage.ServerLevelData;

record WorldState(long dayTime, int clearTime, int rainTime, int thunderTime,
                  boolean raining, boolean thundering, float rainLevel, float thunderLevel) {

    static WorldState capture(MinecraftServer server) {
        ServerLevelData weather = server.getWorldData().overworldData();
        ServerLevel overworld = server.overworld();
        return new WorldState(overworld.getDayTime(), weather.getClearWeatherTime(), weather.getRainTime(),
                weather.getThunderTime(), weather.isRaining(), weather.isThundering(),
                overworld.getRainLevel(1.0F), overworld.getThunderLevel(1.0F));
    }

    boolean sameAs(WorldState other) {
        return other != null && dayTime == other.dayTime && clearTime == other.clearTime && rainTime == other.rainTime
                && thunderTime == other.thunderTime && raining == other.raining && thundering == other.thundering
                && rainLevel == other.rainLevel && thunderLevel == other.thunderLevel;
    }

    void restore(MinecraftServer server) {
        for (ServerLevel level : server.getAllLevels()) {
            level.setDayTime(dayTime);
        }
        server.forceTimeSynchronization();

        ServerLevelData weather = server.getWorldData().overworldData();
        boolean wasRaining = weather.isRaining();
        weather.setClearWeatherTime(clearTime);
        weather.setRainTime(rainTime);
        weather.setThunderTime(thunderTime);
        weather.setRaining(raining);
        weather.setThundering(thundering);
        ServerLevel overworld = server.overworld();
        overworld.setRainLevel(rainLevel);
        overworld.setThunderLevel(thunderLevel);
        var players = server.getPlayerList();
        if (wasRaining != raining) {
            players.broadcastAll(new ClientboundGameEventPacket(
                    raining ? ClientboundGameEventPacket.START_RAINING : ClientboundGameEventPacket.STOP_RAINING, 0.0F),
                    overworld.dimension());
        }
        players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.RAIN_LEVEL_CHANGE, rainLevel),
                overworld.dimension());
        players.broadcastAll(new ClientboundGameEventPacket(ClientboundGameEventPacket.THUNDER_LEVEL_CHANGE, thunderLevel),
                overworld.dimension());
    }
}
