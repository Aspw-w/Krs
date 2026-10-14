package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.BlockEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.ListValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.FluidTags;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;

public class Jesus extends Module {
    private static final VoxelShape WATER_SHAPE = Shapes.create(new AABB(0.0D, 0.0D, 0.0D, 1.0D, 0.9999D, 1.0D));

    private boolean wasInLiquid;

    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Vanilla", "Verus", "Bouncy", "Mini Jump"}, "Vanilla");

    public Jesus() {
        super("Jesus", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        wasInLiquid = false;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onBlock(BlockEvent event) {
        var player = mc.player;
        if (player == null || mc.level == null || !usesSolidCollision(mode.get()) || !isLiquid(event.blockState))
            return;

        Entity vehicle = player.getVehicle();
        boolean canStandOnLiquid = isLiquidBelow(player, false)
                || isLiquidBelow(vehicle, false) && vehicle.fallDistance < 3.0F;

        if (!player.isShiftKeyDown()
                && player.fallDistance < 3.0F
                && !isLiquidAtFeet(player)
                && canStandOnLiquid
                && isEntityAboveBlock(player, event.blockPos))
            event.voxelShape = WATER_SHAPE;
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null) return;

        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "verus" -> {
                if (!player.isInWater()) return;

                if (player.hasEffect(MobEffects.SPEED))
                    MovementUtil.strafe(0.38f);
                else MovementUtil.strafe(0.33f);
            }
            case "bouncy" -> handleWaterMode(0.6D, true);
            case "mini jump" -> handleWaterMode(0.2D, false);
        }
    }

    private void handleWaterMode(double liquidBoost, boolean bouncy) {
        var player = mc.player;
        if (player == null) return;

        if (player.isShiftKeyDown())
            return;

        if (isLiquidAtFeet(player) && !isSneakInputDown()) {
            MovementUtil.setVelocityY(0.1D);
            return;
        }

        if (player.onGround() || player.onClimbable())
            wasInLiquid = false;

        double motionY = player.getDeltaMovement().y;
        if (motionY > 0.0D && wasInLiquid) {
            if (motionY < 0.03D) {
                pushVelocityY(0.06713D);
            } else if (motionY <= 0.05D) {
                if (bouncy) scaleVelocityY(1.20000000999D);
                pushVelocityY(0.06D);
            } else if (motionY <= 0.08D) {
                if (bouncy) scaleVelocityY(1.20000003D);
                pushVelocityY(0.055D);
            } else if (motionY <= 0.112D) {
                pushVelocityY(0.0535D);
            } else {
                scaleVelocityY(bouncy ? 1.000000000002D : 0.500000000002D);
                pushVelocityY(0.0517D);
            }
        }

        motionY = player.getDeltaMovement().y;
        if (wasInLiquid && motionY < 0.0D && motionY > -0.3D)
            pushVelocityY(0.045835D);

        player.fallDistance = 0.0F;

        if (!isLiquidBelow(player, true))
            return;

        MovementUtil.setVelocityY(liquidBoost);

        wasInLiquid = true;
    }

    private static void pushVelocityY(double y) {
        var player = mc.player;
        if (player == null) return;

        player.push(0.0D, y, 0.0D);
    }

    private static void scaleVelocityY(double multiplier) {
        var player = mc.player;
        if (player == null) return;

        var motion = player.getDeltaMovement();
        player.setDeltaMovement(motion.x, motion.y * multiplier, motion.z);
    }

    private static boolean usesSolidCollision(String value) {
        return value.equalsIgnoreCase("vanilla")
                || value.equalsIgnoreCase("bouncy")
                || value.equalsIgnoreCase("mini jump");
    }

    private static boolean isSneakInputDown() {
        var player = mc.player;
        return player != null && (InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyShift.saveString()).getValue())
                || mc.options.keyShift.isDown());
    }

    private static boolean isLiquidBelow(Entity entity, boolean shallow) {
        if (entity == null) return false;

        double offset = shallow ? 0.03D : entity instanceof Player ? 0.2D : 0.5D;
        return isLiquidAt(entity, entity.getY() - offset, true);
    }

    private static boolean isLiquidAtFeet(Entity entity) {
        return entity != null && isLiquidAt(entity, entity.getY() + 0.01D, false);
    }

    private static boolean isLiquidAt(Entity entity, double y, boolean floorY) {
        var level = mc.level;
        if (entity == null || level == null) return false;

        AABB box = entity.getBoundingBox();
        int minX = Mth.floor(box.minX);
        int maxX = Mth.ceil(box.maxX);
        int minZ = Mth.floor(box.minZ);
        int maxZ = Mth.ceil(box.maxZ);
        int blockY = floorY ? Mth.floor(y) : (int) y;
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();

        for (int x = minX; x < maxX; x++) {
            for (int z = minZ; z < maxZ; z++) {
                pos.set(x, blockY, z);

                if (isLiquid(level.getBlockState(pos)))
                    return true;
            }
        }

        return false;
    }

    private static boolean isLiquid(BlockState state) {
        var fluidState = state.getFluidState();
        return fluidState.is(FluidTags.WATER) || fluidState.is(FluidTags.LAVA);
    }

    private static boolean isEntityAboveBlock(Entity entity, BlockPos pos) {
        return entity.getY() >= pos.getY();
    }
}
