package dev.rewind.fabric;

import dev.rewind.RewindSettings;
import dev.rewind.client.KeyRegistrar;
import dev.rewind.client.RewindClient;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.loader.api.FabricLoader;

public final class RewindFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RewindSettings.setFile(FabricLoader.getInstance().getConfigDir().resolve("rewind.properties"));
        RewindClient.setKey(KeyRegistrar.register(RewindClient.createKey()));
        ClientTickEvents.END_CLIENT_TICK.register(RewindClient::tick);
    }
}
