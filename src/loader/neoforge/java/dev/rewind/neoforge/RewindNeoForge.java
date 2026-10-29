package dev.rewind.neoforge;

import dev.rewind.RewindSettings;
import dev.rewind.client.RewindClient;
import dev.rewind.client.gui.SettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.loading.FMLPaths;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = RewindClient.MOD_ID, dist = Dist.CLIENT)
public final class RewindNeoForge {
    public RewindNeoForge(IEventBus modBus, ModContainer container) {
        RewindSettings.setFile(FMLPaths.CONFIGDIR.get().resolve("rewind.properties"));
        KeyMapping key = RewindClient.createKey();
        RewindClient.setKey(key);
        modBus.addListener(RegisterKeyMappingsEvent.class, event -> KeyEvents.register(event, key));
        container.registerExtensionPoint(IConfigScreenFactory.class, (mod, parent) -> new SettingsScreen(parent));
        NeoForge.EVENT_BUS.addListener(ClientTickEvent.Post.class, event -> RewindClient.tick(Minecraft.getInstance()));
    }
}
