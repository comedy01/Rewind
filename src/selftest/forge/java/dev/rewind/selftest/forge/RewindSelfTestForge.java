package dev.rewind.selftest.forge;

import dev.rewind.selftest.RewindSelfTest;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.common.Mod;

@Mod("rewind_selftest")
public final class RewindSelfTestForge {
    public RewindSelfTestForge() {
        RewindSelfTest test = new RewindSelfTest();
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                test.tick();
            }
        });
    }
}
