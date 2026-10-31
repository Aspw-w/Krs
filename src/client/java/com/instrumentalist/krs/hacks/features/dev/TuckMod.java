package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.common.ClientboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundKeepAlivePacket;
import net.minecraft.network.protocol.common.ServerboundPongPacket;
import com.instrumentalist.krs.utils.input.Keys;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class TuckMod extends Module {

    public TuckMod() {
        super("Tuck Mod", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    private int tick;
    private int tick2;
    private int tick3;
    private int tick4;

    @Override
    public void onDisable() {
        tick = 0;
        tick2 = 0;
        tick3 = 0;
        tick4 = 0;
        TimerUtil.reset();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (mc.player == null) return;
        TimerUtil.timerSpeed = 0.5f;
        ChatUtil.printChat("[sex] " + mc.player.getDeltaMovement());
        if (mc.player.getDeltaMovement().y <= -0.3) {
            ChatUtil.printChat("[sex] df");
            event.y += Double.MAX_VALUE;
            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(event.x, event.y - 0.01, event.z, true, true));
            event.onGround = true;
            mc.player.setOnGround(true);
        } else {
            if (mc.player.tickCount % 11 == 0) {
                event.y += Double.MAX_VALUE;
                ChatUtil.printChat("[tungtung] 67fg + " + event.y);
            }
        }
    }

    @Override
    public void onTick(TickEvent event) {
    }

    @Override
    public void onAttack(AttackEvent event) {
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.packet;

        /*if (packet instanceof ServerboundPongPacket ping) {
            tick++;
            if (tick <= 15) {
                ChatUtil.printChat("Nigger");
                PacketUtil.sendPacketAsSilent(new ServerboundPongPacket(Integer.MAX_VALUE));
            } else {
                ChatUtil.printChat("Accept");
                PacketUtil.sendPacketAsSilent(new ServerboundPongPacket(1));
                tick = 0;
            }
        }*/
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
    }
}
