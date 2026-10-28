package com.instrumentalist.krs.utils.packet;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.phys.Vec3;

public final class ServerPositionTracker {
    private static volatile Vec3 lastSentPosition;

    private ServerPositionTracker() {
    }

    public static void recordOutgoing(Packet<?> packet) {
        if (packet instanceof ServerboundMovePlayerPacket movePacket && movePacket.hasPosition())
            lastSentPosition = new Vec3(movePacket.getX(0.0D), movePacket.getY(0.0D), movePacket.getZ(0.0D));
    }

    public static Vec3 lastSentPosition() {
        return lastSentPosition;
    }

    public static void reset() {
        lastSentPosition = null;
    }
}
