package com.instrumentalist.krs.hacks.features.nulling;



import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.math.TickTimer;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.input.Keys;
import xyz.breadloaf.imguimc.customwindow.ModuleRenderable;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundCommandSuggestionsPacket;
import net.minecraft.network.protocol.game.ClientboundPlayerChatPacket;
import net.minecraft.network.protocol.game.ServerboundCommandSuggestionPacket;

public class PluginsDetector extends Module {

    public PluginsDetector() {
        super("Plugins Detector", null, Keys.KEY_UNKNOWN, false, false);
    }

    private final TickTimer timer = new TickTimer();
    private int step = 0;
    public static String[] plugins = null;
    public static List<String> detectedAcs = null;

    @Override
    public void onEnable() {
        if (mc.player == null || mc.level == null) return;
        plugins = null;
        detectedAcs = null;
        step = 0;
        timer.reset();
    }

    @Override
    public void onDisable() {
        if (mc.player == null || mc.level == null) return;
        if (!ModuleManager.plChecked) {
            detectPlugins(false);
            ModuleManager.plChecked = true;
        } else {
            detectPlugins(true);
        }
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        timer.update();
        if (timer.hasTimePassed(20)) {
            timer.reset();
            step++;
        }
        if (timer.tick != 1) return;

        switch (step) {
            case 0 -> PacketUtil.sendPacket(new ServerboundCommandSuggestionPacket(0, "/version "));
            case 1 -> PacketUtil.sendPacket(new ServerboundCommandSuggestionPacket(0, "/bukkit:version "));
            case 2, 3 -> { timer.reset(); step++; }
            case 4 -> PacketUtil.sendPacket(new ServerboundCommandSuggestionPacket(0, "/"));
            default -> this.toggle();
        }
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (mc.player == null || mc.level == null) return;

        Packet<?> packet = event.packet;
        if (packet instanceof ClientboundCommandSuggestionsPacket cmdPacket) {
            if (cmdPacket.suggestions().isEmpty()) return;

            if (step == 0 || step == 1) {
                plugins = collectPlugins(cmdPacket, false);
            } else if (step == 4) {
                plugins = collectPlugins(cmdPacket, true);
            }

            if (plugins != null && plugins.length > 0) {
                List<String> antiCheatResults = collectAntiCheats(plugins);
                detectedAcs = antiCheatResults.isEmpty() ? null : antiCheatResults;
                this.toggle();
            }
            event.cancel();
        } else if (packet instanceof ClientboundPlayerChatPacket chatPacket) {
            String content = chatPacket.body().content();
            if (isChatPluginsMessage(content)) {
                plugins = parseChatPlugins(content);

                this.toggle();
                event.cancel();
            }
        }
    }

    private static String[] collectPlugins(ClientboundCommandSuggestionsPacket packet, boolean removeNamespace) {
        LinkedHashSet<String> results = new LinkedHashSet<>();

        for (ClientboundCommandSuggestionsPacket.Entry entry : packet.suggestions()) {
            String suggestion = entry.text();
            if (removeNamespace ? !isNamespacedCommand(suggestion) : !isSimplePluginName(suggestion))
                continue;

            if (removeNamespace) {
                int separator = suggestion.indexOf(':');
                if (separator > 0)
                    suggestion = suggestion.substring(0, separator);
            }

            results.add(suggestion);
        }

        return results.toArray(String[]::new);
    }

    private static boolean isSimplePluginName(String value) {
        return isSimplePluginName(value, 0, value.length());
    }

    private static boolean isNamespacedCommand(String value) {
        int separator = value.indexOf(':');
        if (separator < 1 || separator > 32 || separator != value.lastIndexOf(':'))
            return false;

        int commandLength = value.length() - separator - 1;
        if (commandLength < 1 || commandLength > 31)
            return false;

        for (int i = 0; i < separator; i++) {
            char c = value.charAt(i);
            if (!(c >= 'a' && c <= 'z' || c >= '0' && c <= '9'))
                return false;
        }

        for (int i = separator + 1; i < value.length(); i++) {
            if (!isAsciiLetterOrDigit(value.charAt(i)))
                return false;
        }

        return true;
    }

    private static boolean isChatPluginsMessage(String content) {
        if (!content.startsWith("Plugins ("))
            return false;

        int countEnd = content.indexOf("): ");
        if (countEnd <= "Plugins (".length())
            return false;

        for (int i = "Plugins (".length(); i < countEnd; i++) {
            char c = content.charAt(i);
            if (c < '0' || c > '9')
                return false;
        }

        int pluginStart = countEnd + 3;
        if (pluginStart >= content.length())
            return false;

        int start = pluginStart;
        while (start < content.length()) {
            int end = content.indexOf(", ", start);
            if (end < 0)
                end = content.length();

            if (!isSimplePluginName(content, start, end))
                return false;

            start = end + 2;
        }

        return true;
    }

    private static String[] parseChatPlugins(String content) {
        int start = content.indexOf("): ") + 3;
        ArrayList<String> results = new ArrayList<>();

        while (start < content.length()) {
            int end = content.indexOf(", ", start);
            if (end < 0)
                end = content.length();

            results.add(content.substring(start, end));
            start = end + 2;
        }

        return results.toArray(String[]::new);
    }

    private static boolean isSimplePluginName(String value, int start, int end) {
        int length = end - start;
        if (length < 1 || length > 32)
            return false;

        for (int i = start; i < end; i++) {
            if (!isAsciiLetterOrDigit(value.charAt(i)))
                return false;
        }
        return true;
    }

    private static boolean isAsciiLetterOrDigit(char c) {
        return c >= 'a' && c <= 'z' || c >= 'A' && c <= 'Z' || c >= '0' && c <= '9';
    }

    private static List<String> collectAntiCheats(String[] pluginNames) {
        ArrayList<String> results = new ArrayList<>();

        for (String plugin : pluginNames) {
            if (isAntiCheat(plugin) && !results.contains(plugin))
                results.add(plugin);
        }

        return results;
    }

    public static void detectPlugins(boolean printChat) {
        if (plugins == null || plugins.length == 0) {
            if (printChat) {
                ChatUtil.printChat("Failed!");
                ModuleRenderable.addCommandLog("Failed!");
            }
            return;
        }

        int acCount = 0;
        StringBuilder pluginList = new StringBuilder();
        for (String plugin : plugins) {
            boolean antiCheat = isAntiCheat(plugin);
            if (antiCheat)
                acCount++;

            if (!pluginList.isEmpty())
                pluginList.append(", ");
            if (antiCheat)
                pluginList.append("AC-");
            pluginList.append(plugin);
        }

        if (printChat) {
            String message = "Plugins Detected [All: " + plugins.length + ", AC: " + acCount + "] - " + pluginList;
            ChatUtil.printChat(message);
            ModuleRenderable.addCommandLog(message);
        }
    }

    public static List<String> getAntiCheats() {
        if (detectedAcs == null)
            return null;

        ArrayList<String> results = new ArrayList<>(detectedAcs.size());
        for (String antiCheat : detectedAcs) {
            if (!antiCheat.isEmpty())
                results.add(antiCheat.substring(0, 1).toUpperCase() + antiCheat.substring(1));
        }

        return results;
    }

    private static boolean isAntiCheat(String plugin) {
        return switch (plugin.toLowerCase(Locale.ROOT).replace(" ", "")) {
            case "nocheatplus", "grimac", "aac", "hawk", "intave", "horizon",
                    "vulcan", "spartan", "kauri", "anticheatreloaded", "matrix",
                    "themis", "negativity", "polar", "wave", "storm", "oxa", "light",
                    "fox", "ghost", "godseye" -> true;
            default -> false;
        };
    }
}
