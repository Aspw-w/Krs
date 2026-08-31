package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import org.lwjgl.glfw.GLFW;

import java.util.HashSet;
import java.util.Locale;
import java.util.Set;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

public class NoMoreAutism extends Module {

    public NoMoreAutism() {
        super("No More Autism", ModuleCategory.Dev, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    private final Set<String> nWords = new HashSet<>(Set.of(
            "skid",
            "nightx",
            "nigga",
            "sex",
            "sigma",
            "fake",
            "nigger",
            "life",
            "money",
            "spend",
            "generated"
    ));

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        Packet<?> packet = event.packet;

        if (packet instanceof ClientboundSystemChatPacket(net.minecraft.network.chat.Component content, boolean overlay) && !overlay && nWords.contains(content.getString().toLowerCase(Locale.ROOT))) {
            Client.notificationManager.addNotification("We saved you (It took $1000 from KRS AI data center)", "Removed N-Words!");
            event.cancel();
        }
    }
}
