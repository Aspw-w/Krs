package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.world.effect.MobEffects;

public class VerusSpeed implements SpeedEvent {

    @Override
    public String getName() {
        return "Verus";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (mc.player.onGround()) {
            if (MovementUtil.isMoving())
                mc.player.jumpFromGround();

            if (mc.player.hasEffect(MobEffects.SPEED))
                MovementUtil.strafe(0.53f);
            else MovementUtil.strafe(0.48f);
        } else {
            if (mc.player.hasEffect(MobEffects.SPEED))
                MovementUtil.strafe(0.38f);
            else MovementUtil.strafe(0.33f);
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player != null && mc.player.onGround() && MovementUtil.isMoving())
            mc.options.keyJump.setDown(false);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
    }
}
