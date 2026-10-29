package dev.rewind.mixin;

import dev.rewind.server.Recorder;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Entity.class)
abstract class EntityMixin {
    @Inject(method = "setRemoved", at = @At("HEAD"))
    private void rewind$removed(Entity.RemovalReason reason, CallbackInfo ci) {
        Entity self = (Entity) (Object) this;
        Recorder recorder = Recorder.at(self.level());
        if (recorder != null) {
            recorder.entityRemoved(self, reason);
        }
    }
}
