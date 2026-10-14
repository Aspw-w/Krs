package com.instrumentalist.krs.hacks.features.movement.fly.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.effect.MobEffects;

public class VerusJetpackFly implements FlyEvent {

    public static int ticks;

    @Override
    public String getName() {
        return "Verus Jetpack";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || FlyModule.verusJetpackJumpKeyOnly.get() && !InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyJump.saveString()).getValue())) return;

        if (mc.player.isSprinting()) {
            mc.player.setDeltaMovement(mc.player.getDeltaMovement().x * 0.4, mc.player.getDeltaMovement().y, mc.player.getDeltaMovement().z * 0.4);
        }

        ticks++;

        if (ticks >= 2) {
            mc.player.jumpFromGround();
            ticks = 0;
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