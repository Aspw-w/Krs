package com.instrumentalist.krs.hacks.features.movement.fly.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.InventoryMove;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyEvent;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundUseItemPacket;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class WatchdogLegacyFly implements FlyEvent {

    @Override
    public String getName() {
        return "Watchdog Legacy";
    }

    private enum State {
        HYPIXEL,
        FALL_VANILLA,
        FLAG_VANILLA
    }

    private static final float VANILLA_H_SPEED = 3.0f;
    private static final float FALL_V_SPEED = 0.0f;
    private static final float FLAG_V_SPEED = 2.0f;
    private static final long FLAG_WINDOW_MS = 6000L;
    private static final double FALL_THRESHOLD = 3;

    public static double moveSpeed = 0.0;
    private static volatile boolean flagged = false;
    private static boolean started = false;
    private static boolean airStart = false;
    private static long startTime = 0L;
    private static State state = State.HYPIXEL;
    private static final long PEARL_WINDOW_MS = 3000L;
    private static final long FLAG_FLY_MS = 3000L;
    private static long flagVanillaStartTime = 0L;
    private static volatile long lastPearlTime = 0L;

    public static void trackPearl(Packet<?> packet) {
        LocalPlayer player = mc.player;
        if (player == null || !(packet instanceof ServerboundUseItemPacket useItemPacket)) return;

        ItemStack stack = player.getItemInHand(useItemPacket.hand());
        if (stack.is(Items.ENDER_PEARL) && !player.getCooldowns().isOnCooldown(stack))
            lastPearlTime = System.currentTimeMillis();
    }

    private static boolean inPearlWindow() {
        return lastPearlTime != 0L && System.currentTimeMillis() - lastPearlTime <= PEARL_WINDOW_MS;
    }

    public static void enable() {
        flagged = false;
        started = false;
        airStart = false;
        startTime = 0L;
        state = State.HYPIXEL;

        LocalPlayer player = mc.player;
        if (player != null && !player.onGround() && player.getDeltaMovement().y < 0 && player.fallDistance >= FALL_THRESHOLD) {
            state = State.FALL_VANILLA;
            started = true;
            TimerUtil.reset();
        }
    }

    private static boolean start(LocalPlayer player) {
        if (player.onGround()) {
            if (player.hurtTime > 0)
                return false;

            startTime = System.currentTimeMillis();

            for (int i = 0; i < 65; i++) {
                PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(player.position().x, player.position().y + 0.049, player.position().z, false, false));
                PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(player.position().x, player.position().y, player.position().z, false, false));
            }
            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(player.position().x, player.position().y, player.position().z, true, false));

            player.jumpFromGround();
            TimerUtil.timerSpeed = 0.8f;
            moveSpeed = 1.2;
        } else {
            startTime = System.currentTimeMillis();
            airStart = true;
            moveSpeed = 0.25;
        }
        return true;
    }

    public static void disable() {
        flagged = false;
        started = false;
        airStart = false;
        startTime = 0L;
        state = State.HYPIXEL;
        moveSpeed = 0.0;
        MovementUtil.stopMoving();
        MovementUtil.setVelocityY(0.0);
        TimerUtil.reset();
    }

    private static void vanillaMotion(float vSpeed) {
        float yMotion = 0f;

        if (InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyJump.saveString()).getValue()) && InventoryMove.canMoveFreely())
            yMotion += vSpeed;

        if (InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyShift.saveString()).getValue()) && InventoryMove.canMoveFreely())
            yMotion -= vSpeed;

        MovementUtil.setVelocityY(yMotion);
        MovementUtil.strafe(VANILLA_H_SPEED);
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (inPearlWindow()) {
            flagged = false;
            TimerUtil.reset();
            vanillaMotion(FLAG_V_SPEED);
            return;
        }

        if (flagged) {
            flagged = false;

            if (state == State.HYPIXEL) {
                if (airStart && System.currentTimeMillis() - startTime <= FLAG_WINDOW_MS) {
                    state = State.FLAG_VANILLA;
                    flagVanillaStartTime = System.currentTimeMillis();
                    moveSpeed = 0.0;
                    TimerUtil.reset();
                } else {
                    FlyModule flyModule = ModuleManager.getModule(FlyModule.class);
                    if (flyModule != null)
                        flyModule.setState(false);
                    return;
                }
            }
        }

        if (state == State.FLAG_VANILLA && System.currentTimeMillis() - flagVanillaStartTime > FLAG_FLY_MS) {
            FlyModule flyModule = ModuleManager.getModule(FlyModule.class);
            if (flyModule != null)
                flyModule.setState(false);
            return;
        }

        if (state == State.FALL_VANILLA)
            vanillaMotion(FALL_V_SPEED);
        else if (state == State.FLAG_VANILLA)
            vanillaMotion(FLAG_V_SPEED);
    }

    @Override
    public void onMotion(MotionEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (state != State.HYPIXEL || inPearlWindow()) return;

        event.callInPost = true;

        if (!started) {
            if (event.post)
                started = start(player);
            return;
        }

        event.onGround = true;
        player.getAbilities().flying = false;
        MovementUtil.setVelocityY(0.0);

        if (moveSpeed >= 1) {
            TimerUtil.timerSpeed = 1.5f;
        } else if (moveSpeed >= 0.7) {
            TimerUtil.timerSpeed = 1.3f;
        } else {
            TimerUtil.timerSpeed = 1.1f;
        }

        if (!MovementUtil.isMoving() || player.horizontalCollision)
            moveSpeed = 0.25;

        if (moveSpeed > 0.25)
            moveSpeed -= moveSpeed / 300;

        if (!event.post)
            player.setPos(player.getX(), player.getY() - 8e-6, player.getZ());

        MovementUtil.strafe((float) moveSpeed);
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        if (state != State.HYPIXEL || inPearlWindow()) {
            mc.options.keyShift.setDown(false);
            return;
        }

        if (moveSpeed > 0)
            mc.options.keyJump.setDown(false);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket)
            flagged = true;
    }

    @Override
    public void onBlock(BlockEvent event) {
    }
}
