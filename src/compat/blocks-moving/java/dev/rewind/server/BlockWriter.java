package dev.rewind.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class BlockWriter {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_SUPPRESS_DROPS
            | Block.UPDATE_MOVE_BY_PISTON;

    private BlockWriter() {
    }

    static void set(ServerLevel level, BlockPos pos, BlockState state) {
        if (!level.getBlockState(pos).is(state.getBlock())) {
            level.removeBlockEntity(pos);
        }
        level.setBlock(pos, state, FLAGS);
    }
}
