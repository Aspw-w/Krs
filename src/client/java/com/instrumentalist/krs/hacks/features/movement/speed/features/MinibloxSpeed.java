package com.instrumentalist.krs.hacks.features.movement.speed.features;



import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;

public class MinibloxSpeed implements SpeedEvent {

    @Override
    public String getName() {
        return "Miniblox";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (mc.player.onGround()) {
            MovementUtil.strafe(0.36f);
            if (MovementUtil.isMoving())
                mc.player.input.makeJump();
        } else {
            switch (MovementUtil.fallTicks) {
                case 3:
                    MovementUtil.setVelocityY(-0.2);
                    break;

                case 5:
                    MovementUtil.strafe(0.7f);
                    MovementUtil.setVelocityY(0.3);
                    break;

                case 10:
                    MovementUtil.strafe(0.8f);
                    MovementUtil.setVelocityY(0.2);
                    break;

                case 18:
                    MovementUtil.strafe(0.6f);
                    MovementUtil.setVelocityY(0.2);
                    break;

                default:
                    MovementUtil.strafe(0.3f);
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
    }
}