package dev.rewind.forge;

import dev.rewind.RewindSettings;
import dev.rewind.client.RewindClient;
import dev.rewind.client.gui.SettingsScreen;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.ConfigScreenHandler;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.fml.IExtensionPoint;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;
import net.minecraftforge.network.NetworkConstants;

@Mod(RewindClient.MOD_ID)
public final class RewindForge {
    public RewindForge() {
        ModLoadingContext context = ModLoadingContext.get();
        context.registerExtensionPoint(IExtensionPoint.DisplayTest.class,
                () -> new IExtensionPoint.DisplayTest(() -> NetworkConstants.IGNORESERVERONLY, (remote, fromServer) -> true));
        if (FMLEnvironment.dist != Dist.CLIENT) {
            return;
        }
        RewindSettings.setFile(FMLPaths.CONFIGDIR.get().resolve("rewind.properties"));
        KeyMapping key = RewindClient.createKey();
        RewindClient.setKey(key);
        FMLJavaModLoadingContext.get().getModEventBus()
                .addListener((RegisterKeyMappingsEvent event) -> event.register(key));
        context.registerExtensionPoint(ConfigScreenHandler.ConfigScreenFactory.class,
                () -> new ConfigScreenHandler.ConfigScreenFactory((mc, parent) -> new SettingsScreen(parent)));
        MinecraftForge.EVENT_BUS.addListener((TickEvent.ClientTickEvent event) -> {
            if (event.phase == TickEvent.Phase.END) {
                RewindClient.tick(Minecraft.getInstance());
            }
        });
    }
}
