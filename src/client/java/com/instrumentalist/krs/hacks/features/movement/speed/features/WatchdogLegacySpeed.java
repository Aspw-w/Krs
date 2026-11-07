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

        if (MovementUtil.isMoving()) {
            if (mc.player.onGround()) {
                TimerUtil.timerSpeed = 1.5f;
                MovementUtil.setVelocityY(0.4);

                int speedLevel = MovementUtil.getSpeedEffect();
                if (speedLevel >= 2) {
                    MovementUtil.strafe(0.65f);
                } else if (speedLevel == 1) {
                    MovementUtil.strafe(0.63f);
                } else {
                    MovementUtil.strafe(0.51f);
                }

                boost = true;
            } else {
                if (boost) {
                    switch (MovementUtil.fallTicks) {
                        case 3:
                            MovementUtil.setVelocityY(0.145);
                            break;

                        case 4:
                            MovementUtil.setVelocityY(0.063);
                            break;

                        case 5:
                            MovementUtil.setVelocityY(-0.19);
                            break;

                        case 6:
                            MovementUtil.setVelocityY(-0.26);
                            break;

                        case 7:
                            MovementUtil.setVelocityY(-0.6);
                            boost = false;
                            break;
                    }
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