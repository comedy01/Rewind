package dev.rewind.mixin;

import dev.rewind.server.Recorder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.function.BooleanSupplier;

@Mixin(ServerLevel.class)
abstract class ServerLevelMixin {
    @Inject(method = "tick", at = @At("HEAD"), cancellable = true)
    private void rewind$frozen(BooleanSupplier haveTime, CallbackInfo ci) {
        if (Recorder.freezes((ServerLevel) (Object) this)) {
            ci.cancel();
        }
    }

    @Inject(method = "addEntity", at = @At("HEAD"))
    private void rewind$spawned(Entity entity, CallbackInfoReturnable<Boolean> cir) {
        Recorder recorder = Recorder.at((ServerLevel) (Object) this);
        if (recorder != null) {
            recorder.entityAdded(entity);
        }
    }
}
