package com.instrumentalist.krs.hacks.features.movement.speed.features;



import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;

public class SmoothVanillaSpeed implements SpeedEvent {

    @Override
    public String getName() {
        return "Smooth Vanilla";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (!mc.player.onGround())
            MovementUtil.smoothStrafe(SpeedModule.vanillaSpeed.get());
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
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