package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.phys.Vec3;

public class WatchdogLegacySpeed implements SpeedEvent {

    private static boolean jumped = false;
    private static int airTicks = -1;
    private static int jumpTicks = 7;
    private static int lagbacks = 0;
    private static int lagbackTicks = 4;
    private static double speed = 0.0;

    @Override
    public String getName() {
        return "Watchdog Legacy";
    }

    public static void enable() {
        speed = mc.player == null ? 0.0 : mc.player.getDeltaMovement().horizontalDistance();
    }

    public static void reset() {
        jumped = false;
        lagbacks = 0;
        airTicks = -1;
        jumpTicks = 7;
    }

    private static int jumpBoostLevel(LocalPlayer player) {
        MobEffectInstance effect = player.getEffect(MobEffects.JUMP_BOOST);
        return effect == null ? 0 : effect.getAmplifier() + 1;
    }

    private static boolean shouldPause(LocalPlayer player) {
        return player.isInWater() || player.isInLava() || ModuleManager.getModuleState(Scaffold.class) && Scaffold.getWasTowering();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || shouldPause(player)) return;

        jumpTicks++;

        if (player.onGround() && MovementUtil.isMoving() && lagbacks == 0) {
            player.jumpFromGround();
            MovementUtil.setVelocityY(0.41999998688697815 + jumpBoostLevel(player) * 0.1 + 1.0E-14);

            speed = 0.644348756324588 + Math.random() * 1.0E-5 + MovementUtil.getSpeedEffect() * 0.13;
            if (jumpTicks < 5)
                speed /= 1.2;
            else jumpTicks = 0;

            if (lagbackTicks < 4)
                speed /= 1.4;

            jumped = true;
            airTicks = 0;
            TimerUtil.timerSpeed = 1.3f;
        } else if (!player.onGround()) {
            TimerUtil.timerSpeed = 1.1f;
        }
    }

    @Override
    public void onMove(MoveEvent event) {
        LocalPlayer player = mc.player;
        if (player == null) return;

        if (shouldPause(player)) {
            airTicks = -1;
            jumped = false;
            return;
        }

        player.setDeltaMovement(event.x, event.y, event.z);

        if (jumped) {
            jumped = false;
            MovementUtil.strafe((float) speed);
        } else if (player.onGround()) {
            airTicks = -1;

            if (lagbacks > 0) {
                MovementUtil.strafe((float) (0.2873 + MovementUtil.getSpeedEffect() * 0.057));
                lagbacks = 0;
            }
        } else if (airTicks >= 0) {
            airTicks++;

            if (jumpBoostLevel(player) == 0) {
                double motionY = player.getDeltaMovement().y;
                switch (airTicks) {
                    case 1 -> MovementUtil.setVelocityY(motionY * 0.985);
                    case 2 -> MovementUtil.setVelocityY(motionY * 0.98);
                    case 3 -> MovementUtil.setVelocityY(motionY * 0.967);
                }
            }

            double base = MovementUtil.getBaseMoveSpeed(0.2873) - (double) (Math.min(5, lagbacks) / 5 * lagbacks);
            MovementUtil.strafe((float) Math.max(base, speed * 0.99));
        }

        Vec3 motion = player.getDeltaMovement();
        event.x = motion.x;
        event.y = motion.y;
        event.z = motion.z;
    }

    @Override
    public void onMotion(MotionEvent event) {
        LocalPlayer player = mc.player;
        if (event.post || player == null || shouldPause(player)) return;

        if (player.onGround()) {
            event.y += 1.0E-14;
            return;
        }

        if (airTicks < 0 || lagbackTicks <= 1) return;

        if (airTicks != 0) {
            double deltaX = player.getX() - player.xo;
            double deltaZ = player.getZ() - player.zo;
            speed = Math.sqrt(deltaX * deltaX + deltaZ * deltaZ);
        } else speed *= 0.63 + Math.random() * 1.0E-5;
    }

    @Override
    public void onTick(TickEvent event) {
        if (lagbackTicks < 4)
            lagbackTicks++;

        if (mc.player != null && mc.player.onGround() && MovementUtil.isMoving())
            mc.options.keyJump.setDown(false);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket) {
            lagbackTicks = 0;
            lagbacks++;
            speed = 0.0;
        }
    }
}