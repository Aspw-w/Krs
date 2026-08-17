package com.instrumentalist.krs.hacks.features.level;



import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.value.TextValue;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundSystemChatPacket;

public class AutoBypass extends Module {

    public AutoBypass() {
        super("Auto Bypass", ModuleCategory.Level, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Setting
    private static final ListValue mode = new ListValue("Mode", new String[]{"Hypixel", "Cubecraft", "Purple Prison", "Auth Me"}, "Hypixel");

    @Setting
    private static final TextValue password = new TextValue("Password", "aaaaaaaa", () -> mode.get().equalsIgnoreCase("auth me"));

    private static String neededCommand = null;
    private static String neededClickCommand = null;

    @Override
    public void onDisable() {
        neededCommand = null;
        neededClickCommand = null;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (neededClickCommand != null) {
            mc.player.connection.sendUnattendedCommand(Commands.trimOptionalPrefix(neededClickCommand), mc.gui.screen());
            neededClickCommand = null;
        } else if (neededCommand != null) {
            mc.player.connection.sendCommand(neededCommand);
            neededCommand = null;
        }
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (neededCommand != null || neededClickCommand != null) return;

        Packet<?> packet = event.packet;

        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "hypixel":
                if (packet instanceof ClientboundSystemChatPacket chatPacket && !chatPacket.overlay()) {
                    if (chatPacket.content().getString().contains("You were spawned in Limbo.")) {
                        neededCommand = "lobby";
                        Client.notificationManager.addNotification("Auto Chat", "Trying to bypass limbo...");
                    } else if (chatPacket.content().getString().contains("You won! Want to play again?") || chatPacket.content().getString().contains("You died! Want to play again?")) {
                        ClickEvent.RunCommand playAgain = findClickHereCommand(chatPacket.content());
                        if (playAgain != null) {
                            neededClickCommand = playAgain.command();
                            Client.notificationManager.addNotification("Auto Join", "Joining to next game...");
                        } else {
                            Client.notificationManager.addNotification("Auto Join", "Failed to track command");
                        }
                    }
                }
                break;

            case "cubecraft":
                if (packet instanceof ClientboundSystemChatPacket chatPacket && !chatPacket.overlay() && chatPacket.content().getString().contains("Thank you for playing")) {
                    neededCommand = "playagain now";
                    Client.notificationManager.addNotification("Auto Join", "Joining to next game...");
                }
                break;

            case "purple prison":
                if (packet instanceof ClientboundSystemChatPacket chatPacket && !chatPacket.overlay() && chatPacket.content().getString().contains("ALERT! Your inventory is full (Use /sell)")) {
                    neededCommand = "sell";
                    Client.notificationManager.addNotification("Auto Sell", "Sold every items");
                }
                break;

            case "auth me":
                if (packet instanceof ClientboundSystemChatPacket chatPacket && !chatPacket.overlay()) {
                    String message = chatPacket.content().getString();
                    if (message.contains("login")) {
                        neededCommand = "login " + password.get();
                        Client.notificationManager.addNotification("Auto Auth", "Logging in...");
                    } else if (message.contains("register")) {
                        neededCommand = "register " + password.get() + " " + password.get();
                        Client.notificationManager.addNotification("Auto Auth", "Registering...");
                    }
                }
                break;
        }
    }

    private static ClickEvent.RunCommand findClickHereCommand(Component message) {
        ClickEvent.RunCommand firstCommand = null;

        for (Component part : message.toFlatList()) {
            if (!(part.getStyle().getClickEvent() instanceof ClickEvent.RunCommand command))
                continue;

            if (part.getString().toLowerCase(Locale.ROOT).contains("click here"))
                return command;

            if (firstCommand == null)
                firstCommand = command;
        }

        return firstCommand;
    }
}
