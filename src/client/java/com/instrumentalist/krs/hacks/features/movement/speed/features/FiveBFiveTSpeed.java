package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;

public class FiveBFiveTSpeed implements SpeedEvent {

    @Override
    public String getName() {
        return "5b5t";
    }

    public static float boostSpeed = 0f;

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.player.isInWater()) return;

        if (MovementUtil.isMoving()) {
            if (mc.player.onGround()) {
                mc.player.jumpFromGround();

                MovementUtil.strafe((float) Math.max(
                        0.47f + MovementUtil.getSpeedEffect() * 0.1,
                        MovementUtil.getBaseMoveSpeed(0.2873)
                ));
            } else {
                if (MovementUtil.fallTicks == 4) {
                    MovementUtil.strafe(0.43f);
                } else MovementUtil.strafe(Math.max(0.24f, MovementUtil.getSpeed()));
            }
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (mc.player == null || mc.player.isInWater()) return;

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
    }
}