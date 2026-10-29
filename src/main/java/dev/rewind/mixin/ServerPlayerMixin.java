package dev.rewind.mixin;

import dev.rewind.core.Marker;
import dev.rewind.server.Recorder;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ServerPlayer.class)
abstract class ServerPlayerMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void rewind$died(DamageSource source, CallbackInfo ci) {
        ServerPlayer self = (ServerPlayer) (Object) this;
        Recorder recorder = Recorder.at(self.level());
        if (recorder != null) {
            recorder.mark(Marker.Kind.DEATH, source.getLocalizedDeathMessage(self).getString());
        }
    }
}
