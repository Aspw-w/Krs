package com.instrumentalist.krs.hacks.features.movement.speed.features;



import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.mojang.blaze3d.platform.InputConstants;

public class VanillaSpeed implements SpeedEvent {

    @Override
    public String getName() {
        return "Vanilla";
    }

    public static boolean cancelledOnce = false;

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (SpeedModule.vanillaAutoBHop.get() && mc.player.onGround() && MovementUtil.isMoving())
            mc.player.input.makeJump();

        MovementUtil.strafe(SpeedModule.vanillaSpeed.get());
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        if (SpeedModule.vanillaAutoBHop.get() && mc.player.onGround() && MovementUtil.isMoving()) {
            mc.options.keyJump.setDown(false);
            cancelledOnce = true;
        } else if (cancelledOnce && mc.player.onGround()) {
            mc.options.keyJump.setDown(InputConstants.isKeyDown(mc.getWindow(), InputConstants.getKey(mc.options.keyJump.saveString()).getValue()));
            cancelledOnce = false;
        }
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
    }
}