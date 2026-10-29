package dev.rewind.mixin;

import dev.rewind.RewindSettings;
import dev.rewind.client.RewindClient;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.DeathScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(DeathScreen.class)
abstract class DeathScreenMixin extends Screen {
    protected DeathScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "init", at = @At("TAIL"))
    private void rewind$addButton(CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (!RewindSettings.deathButton() || !RewindClient.available(mc)) {
            return;
        }
        addRenderableWidget(Button.builder(Component.translatable("rewind.death.button"), button -> {
            button.active = false;
            RewindClient.rewindAfterDeath(mc);
        }).bounds(width / 2 - 100, height / 4 + 120, 200, 20).build());
    }
}
