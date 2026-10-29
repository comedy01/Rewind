package dev.rewind.mixin;

import dev.rewind.server.Recorder;
import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(LevelChunk.class)
abstract class LevelChunkMixin {
    @Inject(method = "setBlockState", at = @At("HEAD"))
    private void rewind$blockChanging(BlockPos pos, BlockState state, boolean moving, CallbackInfoReturnable<BlockState> cir) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        Recorder recorder = Recorder.at(chunk.getLevel());
        if (recorder != null) {
            recorder.blockChanging((ServerLevel) chunk.getLevel(), pos, chunk.getBlockState(pos),
                    chunk.getBlockEntities().get(pos));
        }
    }

    @Inject(method = "setBlockEntity", at = @At("TAIL"))
    private void rewind$blockEntityLoaded(BlockEntity blockEntity, CallbackInfo ci) {
        LevelChunk chunk = (LevelChunk) (Object) this;
        Recorder recorder = Recorder.at(chunk.getLevel());
        if (recorder != null && !blockEntity.isRemoved()) {
            recorder.blockEntityLoaded((ServerLevel) chunk.getLevel(), blockEntity);
        }
    }
}
