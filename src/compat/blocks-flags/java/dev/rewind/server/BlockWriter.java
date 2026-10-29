package dev.rewind.server;

import net.minecraft.core.BlockPos;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.state.BlockState;

final class BlockWriter {
    private static final int FLAGS = Block.UPDATE_CLIENTS | Block.UPDATE_SKIP_ALL_SIDEEFFECTS;

    private BlockWriter() {
    }

    static void set(ServerLevel level, BlockPos pos, BlockState state) {
        level.setBlock(pos, state, FLAGS);
    }
}
