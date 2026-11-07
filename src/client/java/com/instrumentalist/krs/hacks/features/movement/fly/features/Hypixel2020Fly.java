package com.instrumentalist.krs.hacks.features.movement.fly.features;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyEvent;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

public class Hypixel2020Fly implements FlyEvent {

    @Override
    public String getName() {
        return "Hypixel 2020";
    }

    public static double moveSpeed = 0.0;
    private static boolean flagged = false;
    private static boolean started = false;

    public static void enable() {
        flagged = false;
        started = false;
    }

    private static void start(LocalPlayer player) {
        if (player.onGround()) {
            for (int i = 0; i < 65; i++) {
                PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(player.position().x, player.position().y + 0.049, player.position().z, false, false));
                PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(player.position().x, player.position().y, player.position().z, false, false));
            }
            PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(player.position().x, player.position().y, player.position().z, true, false));

            player.jumpFromGround();
            TimerUtil.timerSpeed = 1.5f;
            MovementUtil.strafe(0.5f);
            moveSpeed = 1;
        } else {
            moveSpeed = 0.25;
        }
    }

    public static void disable() {
        flagged = false;
        started = false;
        moveSpeed = 0.0;
        MovementUtil.stopMoving();
        TimerUtil.reset();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (!flagged) return;

        flagged = false;
        FlyModule flyModule = ModuleManager.getModule(FlyModule.class);
        if (flyModule != null)
            flyModule.setState(false);
    }

    @Override
    public void onMotion(MotionEvent event) {
        var player = mc.player;
        if (player == null) return;

        event.callInPost = true;

        if (!started) {
            if (event.post) {
                start(player);
                started = true;
            }
            return;
        }

        event.onGround = true;
        player.getAbilities().flying = false;
        MovementUtil.setVelocityY(0.0);

        if (moveSpeed >= 0.6) {
            TimerUtil.timerSpeed = 1.5f;
        } else {
            TimerUtil.timerSpeed = 1.1f;
        }

        if (!MovementUtil.isMoving() || player.horizontalCollision)
            moveSpeed = 0.25;

        if (moveSpeed > 0.25)
            moveSpeed -= moveSpeed / 159;

        if (!event.post)
            player.setPos(player.getX(), player.getY() - 8e-6, player.getZ());

        MovementUtil.strafe((float) moveSpeed);
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null) return;

        if (moveSpeed > 0)
            mc.options.keyJump.setDown(false);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (event.packet instanceof ClientboundPlayerPositionPacket)
            flagged = true;
    }

    @Override
    public void onBlock(BlockEvent event) {
    }
}
