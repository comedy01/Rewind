package dev.rewind.mixin;

import dev.rewind.server.Recorder;
import net.minecraft.server.MinecraftServer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.function.BooleanSupplier;

@Mixin(MinecraftServer.class)
abstract class MinecraftServerMixin {
    @Inject(method = "tickChildren", at = @At("TAIL"))
    private void rewind$tickEnd(BooleanSupplier haveTime, CallbackInfo ci) {
        Recorder recorder = Recorder.forServer((MinecraftServer) (Object) this);
        if (recorder != null) {
            recorder.tickEnd();
        }
    }

    @Inject(method = "stopServer", at = @At("HEAD"))
    private void rewind$stopped(CallbackInfo ci) {
        Recorder.stopped((MinecraftServer) (Object) this);
    }
}
