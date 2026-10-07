package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;

public class BoostSpeed implements SpeedEvent {

    @Override
    public String getName() {
        return "Boost";
    }

    public static float boostSpeed = 0f;

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.player.isInWater()) return;

        if (MovementUtil.isMoving()) {
            if (mc.player.onGround()) {
                MovementUtil.strafe(Math.max(0.24f, boostSpeed));
                mc.player.input.makeJump();
                if (!mc.player.horizontalCollision)
                    boostSpeed = Math.min(boostSpeed + 0.1f, 1f);
            } else MovementUtil.strafe(Math.max(0.24f, MovementUtil.getSpeed()));
        } else boostSpeed = 0f;
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