package dev.rewind.mixin;

import dev.rewind.core.Marker;
import dev.rewind.server.Recorder;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Explosion;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(ServerLevel.class)
abstract class ExplosionMixin {
    @Inject(method = "explode", at = @At("HEAD"))
    private void rewind$exploded(CallbackInfoReturnable<Explosion> cir) {
        Recorder recorder = Recorder.at((ServerLevel) (Object) this);
        if (recorder != null) {
            recorder.mark(Marker.Kind.EXPLOSION, "Explosion");
        }
    }
}
