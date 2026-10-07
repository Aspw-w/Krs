package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;

public class VulcanSpeed implements SpeedEvent {

    @Override
    public String getName() {
        return "Vulcan";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (!MovementUtil.isMoving()) {
            return;
        }

        if (mc.player.onGround()) {
            MovementUtil.strafe(MovementUtil.getSpeed());
            mc.player.input.makeJump();
            return;
        }

        switch (MovementUtil.fallTicks) {
            case 2, 9:
                MovementUtil.strafe(MovementUtil.getSpeed());
                break;
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player != null && MovementUtil.isMoving())
            mc.options.keyJump.setDown(false);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
    }
}
