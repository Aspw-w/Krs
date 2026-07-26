package com.instrumentalist.krs.hacks.features.movement.speed.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.network.protocol.game.ServerboundPlayerInputPacket;
import net.minecraft.world.entity.player.Input;

public class ModernMMCSpeed implements SpeedEvent {

    public static int tick1;
    public static int tick2;

    @Override
    public String getName() {
        return "Modern MMC";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (mc.player == null) return;

        if (MovementUtil.isMoving()) {
            if (mc.player.onGround()) {
                mc.player.jumpFromGround();
            }
        }
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player != null && mc.player.onGround() && MovementUtil.isMoving())
            mc.options.keyJump.setDown(false);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.packet;

        if (packet instanceof ServerboundMovePlayerPacket) {
            PacketUtil.sendPacket(new ServerboundPlayerInputPacket(new Input(false, false, false, false, true, true, false)));
        }
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
    }
}