package com.instrumentalist.krs.hacks.features.movement.speed.features;



import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.ChatUtil;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;

public class WatchdogLegacySpeed implements SpeedEvent {

    private static boolean flagged = false;

    @Override
    public String getName() {
        return "Watchdog Legacy";
    }

    public static void reset() {
        flagged = false;
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (flagged) {
            flagged = false;
            SpeedModule speedModule = ModuleManager.getModule(SpeedModule.class);
            if (speedModule != null)
                speedModule.setState(false);
            return;
        }

        if (mc.player == null || mc.player.isInWater()) return;

        if (MovementUtil.isMoving()) {
            if (mc.player.onGround()) {
                TimerUtil.timerSpeed = 1.5f;
                MovementUtil.setVelocityY(0.4);
                MovementUtil.strafe((float) Math.max(
                        0.5f + MovementUtil.getSpeedEffect() * 0.1,
                        MovementUtil.getBaseMoveSpeed(0.2873)
                ));
            } else {
                if (MovementUtil.fallTicks == 5) {
                    MovementUtil.setVelocityY(-0.16);
                }

                if (MovementUtil.fallTicks == 8) {
                    MovementUtil.setVelocityY(-0.4);
                }

                TimerUtil.timerSpeed = 1.1f;
                if (mc.player.hurtTime >= 8) {
                    MovementUtil.strafe(Math.max(0.4f, MovementUtil.getSpeed()));
                } else if (mc.player.fallDistance > 0.1) {
                    MovementUtil.strafe(Math.max(0.3f, MovementUtil.getSpeed()));
                } else {
                    MovementUtil.strafe(Math.max(0.24f, MovementUtil.getSpeed()));
                }
            }
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        if (mc.player.onGround() && MovementUtil.isMoving())
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
}