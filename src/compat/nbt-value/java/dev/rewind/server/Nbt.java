package dev.rewind.server;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.util.ProblemReporter;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.storage.TagValueInput;
import net.minecraft.world.level.storage.TagValueOutput;

final class Nbt {
    private Nbt() {
    }

    static CompoundTag saveEntity(ServerLevel level, Entity entity) {
        TagValueOutput output = TagValueOutput.createWithContext(ProblemReporter.DISCARDING, level.registryAccess());
        return entity.save(output) ? output.buildResult() : null;
    }

    static Entity loadEntity(ServerLevel level, CompoundTag tag) {
        return EntityType.loadEntityRecursive(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag),
                level, EntitySpawnReason.LOAD, e -> e);
    }

    static CompoundTag saveBlockEntity(ServerLevel level, BlockEntity blockEntity) {
        return blockEntity.saveWithFullMetadata(level.registryAccess());
    }

    static void loadBlockEntity(ServerLevel level, BlockEntity blockEntity, CompoundTag tag) {
        blockEntity.loadWithComponents(TagValueInput.create(ProblemReporter.DISCARDING, level.registryAccess(), tag));
    }

    static BlockEntity createBlockEntity(ServerLevel level, BlockPos pos, BlockState state, CompoundTag tag) {
        return BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
    }
}
