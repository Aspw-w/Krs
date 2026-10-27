package com.instrumentalist.krs.utils.world;

import com.instrumentalist.krs.utils.IMinecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Blocks;

public final class BlockUtil implements IMinecraft {
    public static final BlockUtil INSTANCE = new BlockUtil();

    private static final Direction[] DIRECTIONS = Direction.values();
    private static final ThreadLocal<BlockPos.MutableBlockPos> ADJACENT_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);

    private BlockUtil() {
    }

    public boolean isFullSurroundedBlock(BlockGetter world, BlockPos pos) {
        if (world == null) return true;

        BlockPos.MutableBlockPos adjacentPos = ADJACENT_POS.get();
        for (Direction direction : DIRECTIONS) {
            var adjacentState = world.getBlockState(adjacentPos.setWithOffset(pos, direction));
            if (adjacentState.is(Blocks.AIR) || adjacentState.is(Blocks.WATER) || adjacentState.is(Blocks.LAVA))
                return true;
        }

        return false;
    }
}
