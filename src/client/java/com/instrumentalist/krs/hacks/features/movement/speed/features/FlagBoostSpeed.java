package com.instrumentalist.krs.hacks.features.movement.speed.features;



import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedEvent;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;

public class FlagBoostSpeed implements SpeedEvent {

    public static int tick1;

    @Override
    public String getName() {
        return "Flag Boost";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (tick1 >= 7 && tick1 != 9 && tick1 != 10 && tick1 < 12) {
            MovementUtil.stopXZ();
            tick1++;
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (mc.player == null) return;

        if (tick1 >= 12) {
            tick1 = 0;
            return;
        }

        if (tick1 < 3) {
            tick1++;
        }

        switch (tick1) {
            case 3:
                if (mc.player.onGround()) {
                    event.y -= 1;
                } else {
                    event.y -= 3;
                }
                break;

            case 4, 6:
                tick1++;
                break;

            case 5:
                event.cancel();
                MovementUtil.strafe(SpeedModule.vanillaSpeed.get());
                tick1++;
                break;

            case 9, 10:
                MovementUtil.strafe(SpeedModule.vanillaSpeed.get());
                tick1++;
                break;
        }
    }

    @Override
    public void onTick(TickEvent event) {
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.packet;

        if (packet instanceof ClientboundPlayerPositionPacket && tick1 == 3) {
            tick1 = 4;
        }
    }
}