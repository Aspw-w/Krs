package com.instrumentalist.krs.hacks.features.movement.fly.features;


import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.InventoryMove;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.mojang.blaze3d.platform.InputConstants;

public class JetpackFly implements FlyEvent {

    @Override
    public String getName() {
        return "Jetpack";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (InputConstants.isKeyDown(mc.getWindow(), InputConstants.getKey(mc.options.keyJump.saveString()).getValue()) && InventoryMove.canMoveFreely()) {
            MovementUtil.setVelocityY(mc.player.getDeltaMovement().y + 0.14);
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
    }

    @Override
    public void onBlock(BlockEvent event) {
    }
}