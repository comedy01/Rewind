package dev.rewind.selftest.fabric;

import dev.rewind.selftest.RewindSelfTest;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;

public final class RewindSelfTestFabric implements ClientModInitializer {
    @Override
    public void onInitializeClient() {
        RewindSelfTest test = new RewindSelfTest();
        ClientTickEvents.END_CLIENT_TICK.register(client -> test.tick());
    }
}
