package dev.rewind.selftest.neoforge;

import dev.rewind.selftest.RewindSelfTest;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.fml.common.Mod;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = "rewind_selftest", dist = Dist.CLIENT)
public final class RewindSelfTestNeoForge {
    public RewindSelfTestNeoForge() {
        RewindSelfTest test = new RewindSelfTest();
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> test.tick());
    }
}
