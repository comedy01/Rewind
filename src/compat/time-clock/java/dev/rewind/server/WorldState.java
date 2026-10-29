package dev.rewind.server;

import net.minecraft.core.Holder;
import net.minecraft.network.protocol.game.ClientboundGameEventPacket;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.clock.ClockState;
import net.minecraft.world.clock.PackedClockStates;
import net.minecraft.world.clock.WorldClock;
import net.minecraft.world.level.saveddata.WeatherData;

import java.util.Map;

record WorldState(PackedClockStates clocks, int clearTime, int rainTime, int thunderTime,
                  boolean raining, boolean thundering, float rainLevel, float thunderLevel) {

    static WorldState capture(MinecraftServer server) {
        WeatherData weather = server.getWeatherData();
        ServerLevel overworld = server.overworld();
        return new WorldState(server.clockManager().packState(), weather.getClearWeatherTime(), weather.getRainTime(),
                weather.getThunderTime(), weather.isRaining(), weather.isThundering(),
                overworld.getRainLevel(1.0F), overworld.getThunderLevel(1.0F));
    }

    boolean sameAs(WorldState other) {
        return other != null && clocks.equals(other.clocks) && clearTime == other.clearTime && rainTime == other.rainTime
                && thunderTime == other.thunderTime && raining == other.raining && thundering == other.thundering
                && rainLevel == other.rainLevel && thunderLevel == other.thunderLevel;
    }

    void restore(MinecraftServer server) {
        for (Map.Entry<Holder<WorldClock>, ClockState> clock : clocks.clocks().entrySet()) {
            server.clockManager().setTotalTicks(clock.getKey(), clock.getValue().totalTicks());
        }
        server.getPlayerList().broadcastAll(server.clockManager().createFullSyncPacket());

        WeatherData weather = server.getWeatherData();
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
