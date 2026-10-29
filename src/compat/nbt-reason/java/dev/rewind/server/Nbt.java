package dev.rewind.server;

import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntitySpawnReason;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;

final class Nbt {
    private Nbt() {
    }

    static CompoundTag saveEntity(ServerLevel level, Entity entity) {
        CompoundTag tag = new CompoundTag();
        return entity.save(tag) ? tag : null;
    }

    static Entity loadEntity(ServerLevel level, CompoundTag tag) {
        return EntityType.loadEntityRecursive(tag, level, EntitySpawnReason.LOAD, e -> e);
    }

    static CompoundTag saveBlockEntity(ServerLevel level, BlockEntity blockEntity) {
        return blockEntity.saveWithFullMetadata(level.registryAccess());
    }

    static void loadBlockEntity(ServerLevel level, BlockEntity blockEntity, CompoundTag tag) {
        blockEntity.loadWithComponents(tag, level.registryAccess());
    }

    static BlockEntity createBlockEntity(ServerLevel level, BlockPos pos, BlockState state, CompoundTag tag) {
        return BlockEntity.loadStatic(pos, state, tag, level.registryAccess());
    }
}
