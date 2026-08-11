package com.instrumentalist.krs.hacks.features.movement.fly.features;



import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyEvent;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class NCPBoostFly implements FlyEvent {
    public static float moveSpeed;
    public static boolean timer;

    @Override
    public String getName() {
        return "NCP Boost";
    }

    @Override
    public void onUpdate(UpdateEvent event) {
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (mc.player == null) return;

        event.callInPost = true;

        if (!event.post) {
            if (timer && !mc.player.onGround()) {
                TimerUtil.reset();
                timer = false;
            }

            event.onGround = true;
            if (!MovementUtil.hasXZMotion() && moveSpeed > 0.25f) {
                moveSpeed = 0.25f;
            } else {
                moveSpeed -= moveSpeed / 169f;
            }

            MovementUtil.strafe((float) Math.max(
                    moveSpeed + MovementUtil.getSpeedEffect() * 0.1,
                    MovementUtil.getBaseMoveSpeed(0.2873)
            ));
            MovementUtil.setVelocityY(-8E-6);
        } else {
            if (mc.player.onGround()) {
                if (FlyModule.ncpDamageBoost.get()) {
                    for (int i = 0; i < 65; i++) {
                        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(mc.player.position().x, mc.player.position().y + 0.049, mc.player.position().z, false, false));
                        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(mc.player.position().x, mc.player.position().y, mc.player.position().z, false, false));
                    }
                    PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(mc.player.position().x, mc.player.position().y, mc.player.position().z, true, false));
                }

                TimerUtil.timerSpeed = 0.3f;
                mc.player.jumpFromGround();
                MovementUtil.stopXZ();
                moveSpeed = 1.61f;
                timer = true;
            }
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

        if (packet instanceof ClientboundPlayerPositionPacket) {
            moveSpeed = 0.25f;
        }
    }

    @Override
    public void onBlock(BlockEvent event) {
    }
}