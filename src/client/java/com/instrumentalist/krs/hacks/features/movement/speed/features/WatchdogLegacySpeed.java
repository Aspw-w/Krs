package com.instrumentalist.krs.hacks.features.movement.speed.features;



import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.ChatUtil;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.world.effect.MobEffects;

public class WatchdogLegacySpeed implements SpeedEvent {

    private static boolean flagged = false;
    private static boolean boost = false;

    @Override
    public String getName() {
        return "Watchdog Legacy";
    }

    public static void reset() {
        flagged = false;
        boost = false;
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

        if (mc.player.onGround()) {
            if (MovementUtil.isMoving()) {
                TimerUtil.timerSpeed = 1.5f;
                MovementUtil.setVelocityY(0.4);

                int speedLevel = MovementUtil.getSpeedEffect();
                if (speedLevel >= 2) {
                    MovementUtil.strafe(0.7f);
                } else if (speedLevel == 1) {
                    MovementUtil.strafe(0.61f);
                } else {
                    MovementUtil.strafe(0.51f);
                }

                boost = true;
            }
        } else {
            if (mc.player.tickCount % 2 == 0)  {
                TimerUtil.timerSpeed = 1.2f;
            } else {
                TimerUtil.timerSpeed = 1.1f;
            }

            int speedLevel = MovementUtil.getSpeedEffect();

            if (speedLevel >= 2) {
                MovementUtil.strafe(Math.max(0.4f, MovementUtil.getSpeed()));
            } else if (speedLevel == 1) {
                MovementUtil.strafe(Math.max(0.34f, MovementUtil.getSpeed()));
            } else {
                MovementUtil.strafe(Math.max(0.27f, MovementUtil.getSpeed()));
            }

            boost = false;
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