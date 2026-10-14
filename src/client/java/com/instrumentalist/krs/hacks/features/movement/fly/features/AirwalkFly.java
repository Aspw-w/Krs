package com.instrumentalist.krs.hacks.features.movement.fly.features;



import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyEvent;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.world.phys.shapes.Shapes;

public class AirwalkFly implements FlyEvent {

    @Override
    public String getName() {
        return "Airwalk";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
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
        if (mc.player == null || InputConstants.isKeyDown(InputConstants.getKey(mc.options.keyShift.saveString()).getValue()) || event.blockPos.getY() != mc.player.blockPosition().below().getY()) return;

        event.voxelShape = Shapes.block();
    }
}