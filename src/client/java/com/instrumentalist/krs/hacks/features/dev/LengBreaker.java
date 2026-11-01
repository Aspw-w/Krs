package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.world.QueuedMiner;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;

public class LengBreaker extends QueuedMiner {
    private static final float HARDNESS_TOLERANCE = 1.5F;

    @Setting
    private final ListValue size = new ListValue("Size", new String[]{"3x3", "5x5"}, "3x3");

    @Setting
    private final IntValue depth = new IntValue("Depth", 1, 1, 3);

    @Setting
    private final BooleanValue similarHardness = new BooleanValue("Similar Hardness", true);

    @Setting
    private final BooleanValue liquidSafety = new BooleanValue("Liquid Safety", true);

    @Setting
    private final FloatValue range = new FloatValue("Range", 4.5f, 1f, 6f, "m");

    @Setting
    private final BooleanValue rotations = new BooleanValue("Rotations", false);

    @Setting
    private final IntValue instantSpeed = new IntValue("Instant Blocks/Tick", 4, 1, 16);

    public LengBreaker() {
        super("Leng Breaker", RenderColors.HAMMER);
    }

    @Override
    protected float range() {
        return range.get();
    }

    @Override
    protected boolean rotations() {
        return rotations.get();
    }

    @Override
    protected int instantBlocksPerTick() {
        return instantSpeed.get();
    }

    @Override
    protected void onBlockBrokenByPlayer(BlockPos origin, BlockState state) {
        var player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null)
            return;

        Direction face = mc.hitResult instanceof BlockHitResult hit && hit.getBlockPos().equals(origin)
                ? hit.getDirection()
                : player.getNearestViewDirection().getOpposite();
        Direction inward = face.getOpposite();
        Direction first;
        Direction second;
        switch (face.getAxis()) {
            case X -> {
                first = Direction.UP;
                second = Direction.SOUTH;
            }
            case Y -> {
                first = Direction.EAST;
                second = Direction.SOUTH;
            }
            default -> {
                first = Direction.UP;
                second = Direction.EAST;
            }
        }

        int half = size.get().equals("5x5") ? 2 : 1;
        float originHardness = state.getDestroySpeed(level, origin);
        for (int layer = 0; layer < depth.get(); layer++) {
            BlockPos center = origin.relative(inward, layer);
            for (int a = -half; a <= half; a++) {
                for (int b = -half; b <= half; b++) {
                    BlockPos pos = center.relative(first, a).relative(second, b);
                    if (pos.equals(origin) || isQueued(pos) || !canBreak(pos))
                        continue;
                    if (similarHardness.get() && level.getBlockState(pos).getDestroySpeed(level, pos) > originHardness * HARDNESS_TOLERANCE + 0.5F)
                        continue;
                    if (liquidSafety.get() && touchesLiquid(level, pos))
                        continue;

                    enqueue(pos);
                }
            }
        }
    }

    private static boolean touchesLiquid(ClientLevel level, BlockPos pos) {
        for (Direction direction : Direction.values()) {
            if (!level.getFluidState(pos.relative(direction)).isEmpty())
                return true;
        }
        return false;
    }
}
