package com.instrumentalist.krs.utils.packet;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundClientTickEndPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;

import java.util.function.Consumer;

public final class MovePlayerPacketFixer {
    private static Object currentConnection;
    private static boolean positionSentThisTick;

    private MovePlayerPacketFixer() {
    }

    public static void beforeSend(Object connection, Packet<?> packet, Consumer<Packet<?>> sendRaw) {
        if (connection != currentConnection) {
            currentConnection = connection;
            positionSentThisTick = false;
        }

        if (packet instanceof ServerboundClientTickEndPacket) {
            positionSentThisTick = false;
            return;
        }

        if (!(packet instanceof ServerboundMovePlayerPacket movePacket) || !movePacket.hasPosition())
            return;

        if (positionSentThisTick)
            sendRaw.accept(ServerboundClientTickEndPacket.INSTANCE);

        positionSentThisTick = true;
    }
}
