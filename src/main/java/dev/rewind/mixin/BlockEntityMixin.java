package dev.rewind.mixin;

import dev.rewind.server.Recorder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(BlockEntity.class)
abstract class BlockEntityMixin {
    @Inject(method = "setChanged(Lnet/minecraft/world/level/Level;Lnet/minecraft/core/BlockPos;Lnet/minecraft/world/level/block/state/BlockState;)V",
            at = @At("HEAD"))
    private static void rewind$changed(Level level, BlockPos pos, BlockState state, CallbackInfo ci) {
        Recorder recorder = Recorder.at(level);
        if (recorder == null) {
            return;
        }
        ServerLevel serverLevel = (ServerLevel) level;
        LevelChunk chunk = serverLevel.getChunkSource().getChunkNow(pos.getX() >> 4, pos.getZ() >> 4);
        BlockEntity blockEntity = chunk == null ? null : chunk.getBlockEntities().get(pos);
        if (blockEntity != null) {
            recorder.blockEntityChanged(serverLevel, blockEntity);
        }
    }

    @Inject(method = "setRemoved()V", at = @At("HEAD"))
    private void rewind$removed(CallbackInfo ci) {
        BlockEntity self = (BlockEntity) (Object) this;
        Recorder recorder = Recorder.at(self.getLevel());
        if (recorder != null) {
            recorder.blockEntityRemoved((ServerLevel) self.getLevel(), self);
        }
    }
}
