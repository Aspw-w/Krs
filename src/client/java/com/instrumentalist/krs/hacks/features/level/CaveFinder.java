package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import net.minecraft.client.renderer.block.BlockAndTintGetter;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.level.block.state.BlockState;
import com.instrumentalist.krs.utils.input.Keys;

public class CaveFinder extends Module {
    private static final Direction[] DIRECTIONS = Direction.values();
    private static final ThreadLocal<BlockPos.MutableBlockPos> NEIGHBOUR_POS = ThreadLocal.withInitial(BlockPos.MutableBlockPos::new);
    private static volatile boolean active;

    public CaveFinder() {
        super("Cave Finder", ModuleCategory.Level, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        active = true;
        reloadChunks();
    }

    @Override
    public void onDisable() {
        active = false;
        reloadChunks();
    }

    public static boolean isActive() {
        return active;
    }

    public static Boolean shouldDrawFace(BlockAndTintGetter level, BlockState state, BlockPos pos, Direction face) {
        if (!active || level == null || state == null || pos == null || state.isAir())
            return null;

        if (!state.canOcclude())
            return isCaveSpace(level, pos) ? null : Boolean.FALSE;

        BlockPos.MutableBlockPos neighbourPos = NEIGHBOUR_POS.get();
        if (face != null)
            return isCaveSpace(level, neighbourPos.setWithOffset(pos, face)) ? null : Boolean.FALSE;

        for (Direction direction : DIRECTIONS) {
            if (isCaveSpace(level, neighbourPos.setWithOffset(pos, direction)))
                return null;
        }
        return Boolean.FALSE;
    }

    private static boolean isCaveSpace(BlockAndTintGetter level, BlockPos pos) {
        return !level.getBlockState(pos).canOcclude() && level.getBrightness(LightLayer.SKY, pos) == 0;
    }

    private void reloadChunks() {
        if (mc.level != null)
            mc.levelExtractor.allChanged();
    }
}
