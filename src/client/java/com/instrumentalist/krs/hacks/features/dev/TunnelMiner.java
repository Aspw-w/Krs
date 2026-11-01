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

import java.util.Locale;

public class TunnelMiner extends QueuedMiner {
    @Setting
    private final ListValue size = new ListValue("Size", new String[]{"1x2", "3x3"}, "1x2");

    @Setting
    private final IntValue length = new IntValue("Length", 1, 1, 4);

    @Setting
    private final BooleanValue liquidSafety = new BooleanValue("Liquid Safety", true);

    @Setting
    private final FloatValue range = new FloatValue("Range", 4.5f, 1f, 6f, "m");

    @Setting
    private final BooleanValue rotations = new BooleanValue("Rotations", false);

    @Setting
    private final IntValue instantSpeed = new IntValue("Instant Blocks/Tick", 4, 1, 16);

    public TunnelMiner() {
        super("Tunnel Miner", RenderColors.TUNNEL_MINER);
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

        Direction facing = player.getDirection();
        Direction lateral = facing.getClockWise();
        BlockPos base = player.blockPosition();
        int dx = origin.getX() - base.getX();
        int dz = origin.getZ() - base.getZ();
        int forward = dx * facing.getStepX() + dz * facing.getStepZ();
        int side = dx * lateral.getStepX() + dz * lateral.getStepZ();
        int height = origin.getY() - base.getY();

        boolean wide = size.get().toLowerCase(Locale.ROOT).equals("3x3");
        int halfWidth = wide ? 1 : 0;
        int tunnelHeight = wide ? 3 : 2;
        if (forward < 1 || Math.abs(side) > halfWidth || height < 0 || height >= tunnelHeight)
            return;

        int slices = length.get();
        for (int slice = 0; slice < slices; slice++) {
            BlockPos column = base.relative(facing, forward + slice);
            for (int offset = -halfWidth; offset <= halfWidth; offset++) {
                for (int dy = 0; dy < tunnelHeight; dy++) {
                    BlockPos pos = column.relative(lateral, offset).above(dy);
                    if (pos.equals(origin) || isQueued(pos) || !canBreak(pos))
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
