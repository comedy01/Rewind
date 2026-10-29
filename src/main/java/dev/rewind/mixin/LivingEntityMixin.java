package dev.rewind.mixin;

import dev.rewind.core.Marker;
import dev.rewind.server.Pets;
import dev.rewind.server.Recorder;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(LivingEntity.class)
abstract class LivingEntityMixin {
    @Inject(method = "die", at = @At("HEAD"))
    private void rewind$died(DamageSource source, CallbackInfo ci) {
        LivingEntity self = (LivingEntity) (Object) this;
        if (self instanceof Player || self.isRemoved()) {
            return;
        }
        boolean pet = Pets.owned(self);
        if (!pet && !self.hasCustomName()) {
            return;
        }
        Recorder recorder = Recorder.at(self.level());
        if (recorder != null) {
            recorder.mark(Marker.Kind.PET_DEATH, source.getLocalizedDeathMessage(self).getString());
        }
    }
}
