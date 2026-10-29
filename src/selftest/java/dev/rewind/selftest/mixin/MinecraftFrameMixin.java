package dev.rewind.selftest.mixin;

import dev.rewind.selftest.RewindSelfTest;
import net.minecraft.client.Minecraft;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Minecraft.class)
abstract class MinecraftFrameMixin {
    @Inject(method = "runTick", at = @At("HEAD"))
    private void rewindSelftest$frame(boolean advanceGameTime, CallbackInfo ci) {
        RewindSelfTest.frame();
    }
}
