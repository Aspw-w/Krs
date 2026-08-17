package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.EntityExtension;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class NoFall extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Packet", "Less Packet", "Spoof", "No Ground", "Hypixel NCP", "Cubecraft Reduce", "Grim 1.9+"}, "Packet");

    @Setting
    private final BooleanValue modifiedGrim = new BooleanValue("Modified Grim", false, () -> mode.get().equalsIgnoreCase("grim 1.9+"));

    private int movementFallTicks = 0;
    private boolean timered = false;
    private int timerStage = 0;
    private int fallTicks = 0;
    private boolean grimActive = false;
    private boolean grimJump = false;
    private boolean grimPressedJump = false;
    private boolean grimAwaitingVelocity = false;
    private boolean grimFastFallApplied = false;
    private int grimGroundTicks = 0;
    private float grimFallDistance = 0.0F;
    private double grimLastY = Double.NaN;

    public NoFall() {
        super("No Fall", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        if (timerStage != 0) {
            TimerUtil.reset();
            timerStage = 0;
        }
        timered = false;
        fallTicks = 0;
        movementFallTicks = 0;
        resetGrimState(true);
    }

    @Override
    public void onEnable() {
        resetGrimState(true);
    }

    @Override
    public void onWorld(WorldEvent event) {
        if (timerStage != 0) {
            TimerUtil.reset();
            timerStage = 0;
        }
        timered = false;
        fallTicks = 0;
        movementFallTicks = 0;
        resetGrimState(true);
    }

    @Override
    public void onTick(TickEvent event) {
        var player = mc.player;
        if (player == null || player.isSpectator() || player.isFallFlying() || player.isSwimming()) {
            if (timerStage != 0) {
                TimerUtil.reset();
                timerStage = 0;
            }
            timered = false;
            fallTicks = 0;
            resetGrimState(true);
            return;
        }

        if (mode.get().toLowerCase(Locale.ROOT).equals("hypixel ncp")) {
            if (player.getDeltaMovement().y <= -0.6 && !MovementUtil.isBlockBelow() && !mc.options.keyShift.isDown() && !EntityExtension.isFallingToVoid(player)) {
                switch (timerStage) {
                    case 0 -> {
                        TimerUtil.timerSpeed = 0.5f;
                        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.StatusOnly(true, false));
                        timerStage++;
                        timered = true;
                    }
                    case 1 -> {
                        TimerUtil.reset();
                        timerStage = 0;
                    }
                }
            } else if (timered) {
                TimerUtil.reset();
                timerStage = 0;
                timered = false;
            }
        }

        if (mode.get().equalsIgnoreCase("grim 1.9+")) {
            if (player.onGround())
                grimGroundTicks++;
            else grimGroundTicks = 0;

            if (grimJump) {
                mc.options.keyJump.setDown(true);
                grimPressedJump = true;
                grimJump = false;
                grimActive = false;
                grimAwaitingVelocity = false;
            } else if (grimPressedJump) {
                restoreGrimJumpKey();
                resetGrimCycle();
            } else if (grimAwaitingVelocity) {
                restoreGrimJumpKey();
            } else if (grimActive && !player.onGround()) {
                mc.options.keyJump.setDown(false);
            }
        }
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player != null) {
            if (mc.player.onGround())
                movementFallTicks = 0;
            else movementFallTicks++;
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
        var player = mc.player;
        if (player == null || player.isSpectator() || player.isFallFlying() || player.isSwimming()) {
            if (timerStage != 0) {
                TimerUtil.reset();
                timerStage = 0;
            }
            timered = false;
            fallTicks = 0;
            resetGrimState(true);
            return;
        }

        double motionY = player.getDeltaMovement().y;
        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "packet" -> {
                if (player.onGround()) {
                    fallTicks = 0;
                    return;
                }
                if (motionY < -0.5) {
                    fallTicks++;
                    if (fallTicks > 1) {
                        event.onGround = true;
                        fallTicks = 0;
                    }
                }
            }
            case "less packet" -> {
                if (player.onGround()) {
                    fallTicks = 0;
                    return;
                }
                if (motionY < -0.5) {
                    fallTicks++;
                    if (fallTicks > 1) {
                        event.onGround = true;
                        event.isMoving = false;
                        fallTicks = 0;
                    }
                }
            }
            case "spoof" -> event.onGround = true;
            case "no ground" -> event.onGround = false;
            case "cubecraft reduce" -> {
                if (motionY < -0.5) {
                    double distanceToGround = getDistanceToGround();
                    if (distanceToGround >= 0.0 && distanceToGround <= 2.0 && !hasBlockAbove(2.0) && !hasPowderSnowNearby(2.0)) {
                        event.y += 2;
                        event.onGround = false;
                        event.isMoving = true;
                    }
                }
            }
            case "grim 1.9+" -> {
                if (!Double.isNaN(grimLastY)) {
                    double fall = grimLastY - event.y;
                    if (fall > 0.0)
                        grimFallDistance += (float) fall;
                }
                grimLastY = event.y;

                if (motionY > 0.1) {
                    if (grimPressedJump)
                        restoreGrimJumpKey();
                    resetGrimCycle();
                }

                if (grimFallDistance > 3.0F || player.fallDistance > 3.0F)
                    grimActive = true;

                if (player.onGround() && !grimActive) {
                    grimFallDistance = 0.0F;
                    grimLastY = event.y;
                    grimFastFallApplied = false;
                }

                if (grimActive && player.onGround()) {
                    if (modifiedGrim.get())
                        PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.Pos(player.getX(), player.getY() + 0.01, player.getZ(), true, player.horizontalCollision));

                    PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.StatusOnly(true, player.horizontalCollision));

                    event.cancel();
                    mc.options.keyJump.setDown(false);
                    grimActive = false;
                    grimAwaitingVelocity = true;
                    grimFallDistance = 0.0F;
                    grimLastY = event.y;
                } else if (grimActive) {
                    mc.options.keyJump.setDown(false);
                }

                if (modifiedGrim.get() && !grimFastFallApplied && getDistanceToGround() > 5.0 && movementFallTicks >= 9 && player.tickCount > 200) {
                    double predictedMotion = motionY;
                    for (int i = 0; i < 10; i++)
                        predictedMotion = (predictedMotion - 0.08) * 0.98;
                    MovementUtil.setVelocityY(predictedMotion);
                    grimFastFallApplied = true;
                }
            }
        }
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.packet;

        if (mode.get().equalsIgnoreCase("grim 1.9+") && (grimActive || grimAwaitingVelocity) && packet instanceof ClientboundSetEntityMotionPacket(int id, net.minecraft.world.phys.Vec3 movement) && id == mc.player.getId()) {
            if (movement.y > 0 || mc.player.hurtTime <= 14 || grimGroundTicks <= 1) {
                grimJump = true;
                grimAwaitingVelocity = false;
            }
        }
    }

    private void resetGrimState(boolean resetGroundTicks) {
        resetGrimCycle();
        if (resetGroundTicks)
            grimGroundTicks = 0;
    }

    private void resetGrimCycle() {
        if (mc.player != null)
            restoreGrimJumpKey();
        grimActive = false;
        grimJump = false;
        grimPressedJump = false;
        grimAwaitingVelocity = false;
        grimFastFallApplied = false;
        grimFallDistance = 0.0F;
        grimLastY = Double.NaN;
    }

    private void restoreGrimJumpKey() {
        mc.options.keyJump.setDown(InputConstants.isKeyDown(mc.getWindow(), InputConstants.getKey(mc.options.keyJump.saveString()).getValue()));
    }

    private double getDistanceToGround() {
        var player = mc.player;
        var world = mc.level;
        if (player == null || world == null) return Double.MAX_VALUE;

        AABB playerBox = player.getBoundingBox();
        double playerY = playerBox.minY;
        double closestDistance = Double.MAX_VALUE;

        for (int x = (int) Math.floor(playerBox.minX); x <= (int) Math.floor(playerBox.maxX); x++) {
            for (int y = (int) Math.floor(playerY); y >= (int) Math.floor(playerY - 20.0); y--) {
                for (int z = (int) Math.floor(playerBox.minZ); z <= (int) Math.floor(playerBox.maxZ); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    var state = world.getBlockState(pos);
                    if (state.isAir()) continue;

                    for (AABB box : state.getCollisionShape(world, pos).toAabbs()) {
                        AABB collisionBox = box.move(pos);
                        if (collisionBox.maxY > playerY || collisionBox.maxX <= playerBox.minX || collisionBox.minX >= playerBox.maxX || collisionBox.maxZ <= playerBox.minZ || collisionBox.minZ >= playerBox.maxZ)
                            continue;

                        closestDistance = Math.min(closestDistance, playerY - collisionBox.maxY);
                    }
                }
            }
        }

        return closestDistance;
    }

    private boolean hasBlockAbove(double distance) {
        var player = mc.player;
        var world = mc.level;
        if (player == null || world == null) return true;

        AABB playerBox = player.getBoundingBox();
        AABB checkBox = new AABB(playerBox.minX, playerBox.maxY, playerBox.minZ, playerBox.maxX, playerBox.maxY + distance, playerBox.maxZ);
        for (var shape : world.getBlockCollisions(player, checkBox)) {
            if (!shape.isEmpty())
                return true;
        }

        return false;
    }

    private boolean hasPowderSnowNearby(double distance) {
        var player = mc.player;
        var world = mc.level;
        if (player == null || world == null) return true;

        AABB checkBox = player.getBoundingBox().inflate(distance, distance, distance);
        for (int x = (int) Math.floor(checkBox.minX); x <= (int) Math.floor(checkBox.maxX); x++) {
            for (int y = (int) Math.floor(checkBox.minY); y <= (int) Math.floor(checkBox.maxY); y++) {
                for (int z = (int) Math.floor(checkBox.minZ); z <= (int) Math.floor(checkBox.maxZ); z++) {
                    if (world.getBlockState(new BlockPos(x, y, z)).is(Blocks.POWDER_SNOW))
                        return true;
                }
            }
        }

        return false;
    }
}
