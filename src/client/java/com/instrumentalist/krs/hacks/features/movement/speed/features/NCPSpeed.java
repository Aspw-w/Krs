package com.instrumentalist.krs.hacks.features.movement.speed.features;



import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;

public class NCPSpeed implements SpeedEvent {

    @Override
    public String getName() {
        return "NCP";
    }

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
                MovementUtil.strafe(Math.max(0.24f, MovementUtil.getSpeed()));
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
    public void onMove(MoveEvent event) {
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
    }
}