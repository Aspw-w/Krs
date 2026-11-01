package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.render.BlockHighlight;
import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.util.Mth;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class GhostHand extends Module {
    private static final int MAX_STEPS = 96;

    @Setting
    private static final IntValue depth = new IntValue("Depth", 2, 2, 6);

    @Setting
    private static final BooleanValue onlyWhileAttacking = new BooleanValue("Only While Attacking", true);

    @Setting
    private static final BooleanValue showTarget = new BooleanValue("Show Target", true);

    private final BlockHighlight highlight = new BlockHighlight(0.15f, RenderColors.GHOST_HAND);

    public GhostHand() {
        super("Ghost Hand", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return String.valueOf(depth.get());
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        highlight.release();
    }

    @Override
    public void onWorld(WorldEvent event) {
        highlight.release();
    }

    public static HitResult modifyHitResult(HitResult original) {
        if (!ModuleManager.getModuleState(GhostHand.class) || original instanceof EntityHitResult)
            return original;
        if (onlyWhileAttacking.get() && !mc.options.keyAttack.isDown())
            return original;

        BlockHitResult target = findTarget();
        return target != null ? target : original;
    }

    private static BlockHitResult findTarget() {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || player.isSpectator()
                || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR)
            return null;

        Vec3 start = player.getEyePosition();
        Vec3 direction = player.getViewVector(1.0F);
        double reach = player.blockInteractionRange();
        Vec3 end = start.add(direction.scale(reach));

        int x = Mth.floor(start.x);
        int y = Mth.floor(start.y);
        int z = Mth.floor(start.z);
        int stepX = (int) Math.signum(direction.x);
        int stepY = (int) Math.signum(direction.y);
        int stepZ = (int) Math.signum(direction.z);
        double deltaX = stepX == 0 ? Double.MAX_VALUE : Math.abs(1.0D / direction.x);
        double deltaY = stepY == 0 ? Double.MAX_VALUE : Math.abs(1.0D / direction.y);
        double deltaZ = stepZ == 0 ? Double.MAX_VALUE : Math.abs(1.0D / direction.z);
        double maxX = stepX == 0 ? Double.MAX_VALUE : (stepX > 0 ? x + 1 - start.x : start.x - x) * deltaX;
        double maxY = stepY == 0 ? Double.MAX_VALUE : (stepY > 0 ? y + 1 - start.y : start.y - y) * deltaY;
        double maxZ = stepZ == 0 ? Double.MAX_VALUE : (stepZ > 0 ? z + 1 - start.z : start.z - z) * deltaZ;

        int wanted = depth.get();
        int found = 0;
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        for (int i = 0; i < MAX_STEPS; i++) {
            cursor.set(x, y, z);
            BlockState state = level.getBlockState(cursor);
            if (!state.isAir()) {
                VoxelShape shape = state.getShape(level, cursor);
                if (!shape.isEmpty()) {
                    BlockHitResult hit = shape.clip(start, end, cursor.immutable());
                    if (hit != null && ++found >= wanted)
                        return hit;
                }
            }

            if (maxX < maxY && maxX < maxZ) {
                if (maxX > reach) break;
                x += stepX;
                maxX += deltaX;
            } else if (maxY < maxZ) {
                if (maxY > reach) break;
                y += stepY;
                maxY += deltaY;
            } else {
                if (maxZ > reach) break;
                z += stepZ;
                maxZ += deltaZ;
            }
        }

        return null;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        BlockHitResult target = showTarget.get() ? findTarget() : null;
        if (target == null)
            highlight.release();
        else
            highlight.setTarget(target.getBlockPos());
    }
}
