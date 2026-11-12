package com.instrumentalist.krs.utils.navigation;

import net.minecraft.core.BlockPos;

public record NavMove(BlockPos from, BlockPos to, Type type) {
    public enum Type {
        WALK,
        ASCEND,
        DESCEND,
        PILLAR,
        SWIM_UP,
        DOWN
    }
}
