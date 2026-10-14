package com.instrumentalist.krs.hacks.features.movement.fly.features;



import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.hacks.features.movement.InventoryMove;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.mojang.blaze3d.platform.InputConstants;

public class VanillaFly implements FlyEvent {

    @Override
    public String getName() {
        return "Vanilla";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        float yMotion = 0f;

        if (InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyJump.saveString()).getValue()) && InventoryMove.canMoveFreely())
            yMotion += FlyModule.vanillaVSpeed.get();

        if (InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyShift.saveString()).getValue()) && InventoryMove.canMoveFreely())
            yMotion -= FlyModule.vanillaVSpeed.get();

        MovementUtil.setVelocityY(yMotion);
        MovementUtil.strafe(FlyModule.vanillaHSpeed.get());
    }

    @Override
    public void onMotion(MotionEvent event) {
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        mc.options.keyShift.setDown(false);
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