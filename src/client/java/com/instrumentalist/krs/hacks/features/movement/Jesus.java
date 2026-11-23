package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.BlockEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.level.block.LiquidBlock;
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
    private static final VoxelShape WATER_SHAPE = Shapes.block();
    private static final VoxelShape BOUNCY_WATER_SHAPE = Shapes.box(0.0D, 0.0D, 0.0D, 1.0D, 0.999D, 1.0D);
    private static final double[] BOUNCY_MOTION = {
            0.5D, 0.484D, 0.468D, 0.436D, 0.404D, 0.372D, 0.34D, 0.308D, 0.276D, 0.244D, 0.212D, 0.18D,
            0.166D, 0.166D, 0.156D, 0.123D, 0.135D, 0.111D, 0.086D, 0.098D, 0.073D, 0.048D, 0.06D, 0.036D,
            0.0106D, 0.015D, 0.004D, 0.004D, 0.004D, 0.004D, -0.013D, -0.045D, -0.077D, -0.109D
    };

    private boolean wasInLiquid;
    private int bouncyStage;
    private long bouncyStartTime;

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
        bouncyStage = 0;
    }

    @Override
    public void onEnable() {
        bouncyStage = 0;
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket)
            bouncyStage = 0;
    }

    @Override
    public void onBlock(BlockEvent event) {
        var player = mc.player;
        if (player == null || mc.level == null || !usesSolidCollision(mode.get()) || !isLiquid(event.blockState))
            return;

        if (mode.get().equalsIgnoreCase("bouncy")) {
            if (event.blockState.getBlock() instanceof LiquidBlock
                    && event.blockPos.getY() + 0.9D < player.getBoundingBox().minY
                    && getLiquidLevel(event.blockState) <= 4
                    && !player.isShiftKeyDown()
                    && player.fallDistance < 12.0F)
                event.voxelShape = BOUNCY_WATER_SHAPE;
            return;
        }

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
            case "bouncy" -> handleBouncy();
            case "mini jump" -> handleWaterMode(0.2D, false);
        }
    }

    private void handleBouncy() {
        var player = mc.player;
        if (player == null || mc.level == null) return;

        boolean onLiquidSurface = isStandingAboveShallowLiquid(player);

        if (player.isInWater() && !player.isShiftKeyDown() && onLiquidSurface)
            MovementUtil.setVelocityY(0.09D);

        if (player.onGround() && !player.isInWater() && onLiquidSurface) {
            bouncyStage = 1;
            bouncyStartTime = System.currentTimeMillis();
        }

        if (bouncyStage <= 0 || System.currentTimeMillis() - bouncyStartTime >= 2500L)
            return;

        if (player.verticalCollision && !isOnGround(player, 0.001D) || player.isShiftKeyDown())
            bouncyStage = -1;

        MovementUtil.stopMoving();

        if (!player.isInLiquid() && !player.isInWater())
            MovementUtil.strafe((float) (0.25D + MovementUtil.getSpeedEffect() * 0.05D));

        int index = bouncyStage - 1;
        if (index >= 0 && index < BOUNCY_MOTION.length)
            MovementUtil.setVelocityY(BOUNCY_MOTION[index]);

        bouncyStage++;
    }

    private static boolean isStandingAboveShallowLiquid(Player player) {
        double x = player.getX();
        double y = player.getY();
        double z = player.getZ();
        double[][] offsets = {{0.3D, 0.3D}, {-0.3D, 0.3D}, {0.3D, -0.3D}, {-0.3D, -0.3D}};

        for (double[] offset : offsets) {
            BlockState state = mc.level.getBlockState(BlockPos.containing(x + offset[0], y, z + offset[1]));
            if (state.getBlock() instanceof LiquidBlock && getLiquidLevel(state) <= 4)
                return true;
        }

        return false;
    }

    private static int getLiquidLevel(BlockState state) {
        return state.hasProperty(LiquidBlock.LEVEL) ? state.getValue(LiquidBlock.LEVEL) : -1;
    }

    private static boolean isOnGround(Player player, double height) {
        return !mc.level.noCollision(player, player.getBoundingBox().move(0.0D, -height, 0.0D));
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
