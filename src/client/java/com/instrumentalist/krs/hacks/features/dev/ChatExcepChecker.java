package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.input.Keys;

import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

public class ChatExcepChecker extends Module {

    public ChatExcepChecker() {
        super("Chat Excep Checker", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        Packet<?> packet = event.packet;

        if (packet instanceof ClientboundSystemChatPacket && !((ClientboundSystemChatPacket) packet).overlay()) {
            ChatUtil.printChat(((ClientboundSystemChatPacket) packet).content().getString());
            event.cancel();
        }
    }
}
