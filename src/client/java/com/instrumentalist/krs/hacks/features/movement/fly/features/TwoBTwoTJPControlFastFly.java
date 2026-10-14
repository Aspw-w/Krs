package com.instrumentalist.krs.hacks.features.movement.fly.features;



import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.InventoryMove;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;

public class TwoBTwoTJPControlFastFly implements FlyEvent {

    @Override
    public String getName() {
        return "2b2tJP Control Fast";
    }

    public static int tick = 0;
    public static boolean phasing = false;
    public static float boostSpeed = 1f;

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        AABB boundingBox = mc.player.getBoundingBox();
        Level world = mc.level;

        boolean headOrBodyInBlock = hasSolidCollision(world, boundingBox, boundingBox.minY, boundingBox.maxY - 0.4, boundingBox);

        if (headOrBodyInBlock) {
            mc.player.noPhysics = true;
            phasing = true;
        } else {
            phasing = false;
        }

        if (phasing) {
            if (!hasSolidCollision(world, boundingBox, boundingBox.maxY, boundingBox.maxY + 0.2, boundingBox))
                phasing = false;
        }

        boolean blockAboveHead = hasSolidCollision(world, boundingBox, boundingBox.maxY + 0.01, boundingBox.maxY + 0.2, boundingBox.inflate(0.0, 0.2, 0.0));

        if (!phasing && blockAboveHead
                && mc.player.verticalCollision
                && InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyJump.saveString()).getValue())) {

            switch (tick) {
                case 0:
                    mc.player.input.keyPresses = new Input(mc.player.input.keyPresses.forward(), mc.player.input.keyPresses.backward(), mc.player.input.keyPresses.left(), mc.player.input.keyPresses.right(), true, true, mc.player.input.keyPresses.sprint());
                    tick++;
                    break;

                case 1:
                    mc.player.input.keyPresses = new Input(mc.player.input.keyPresses.forward(), mc.player.input.keyPresses.backward(), mc.player.input.keyPresses.left(), mc.player.input.keyPresses.right(), false, false, mc.player.input.keyPresses.sprint());
                    tick++;
                    break;

                case 2:
                    tick = 0;
                    phasing = hasSolidCollision(world, boundingBox, boundingBox.maxY, boundingBox.maxY + 0.2, boundingBox);
                    break;
            }
        }

        if (InputConstants.isKeyDown(InputConstants.KEY_LCONTROL) && MovementUtil.isMoving()) {
            if (boostSpeed != 1.5f) {
                Client.notificationManager.addNotification("Tesla bypass", "Boosted speed to MAX");
                boostSpeed = 1.5f;
            }
        } else if (boostSpeed > 1f) {
            boostSpeed = 1f;
            Client.notificationManager.addNotification("Reset", "Reset boost speed (cancel)");
        }

        float yMotion = 0f;
        float gain = boostSpeed > 1f ? 0.05f : 1f;

        if (InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyJump.saveString()).getValue()) && InventoryMove.canMoveFreely())
            yMotion += gain;

        if (InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyShift.saveString()).getValue()) && InventoryMove.canMoveFreely())
            yMotion -= gain;

        MovementUtil.setVelocityY((double) yMotion);
        MovementUtil.strafe(boostSpeed);
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        mc.options.keyShift.setDown(false);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.packet;

        if (packet instanceof ClientboundPlayerPositionPacket && boostSpeed > 1f) {
            boostSpeed = 1f;
            Client.notificationManager.addNotification("Reset", "Reset boost speed (flag)");
        }
    }

    @Override
    public void onBlock(BlockEvent event) {
    }

    private static boolean hasSolidCollision(Level world, AABB scanBox, double minY, double maxY, AABB collisionBox) {
        for (int x = (int) Math.floor(scanBox.minX); x <= (int) Math.floor(scanBox.maxX); x++) {
            for (int y = (int) Math.floor(minY); y <= (int) Math.floor(maxY); y++) {
                for (int z = (int) Math.floor(scanBox.minZ); z <= (int) Math.floor(scanBox.maxZ); z++) {
                    BlockPos pos = new BlockPos(x, y, z);
                    BlockState state = world.getBlockState(pos);
                    if (state.isAir()) continue;

                    for (AABB box : state.getCollisionShape(world, pos).toAabbs()) {
                        if (box.move(pos).intersects(collisionBox))
                            return true;
                    }
                }
            }
        }

        return false;
    }
}
