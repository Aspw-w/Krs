package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.mojang.serialization.DynamicOps;
import com.mojang.serialization.JsonOps;
import com.google.gson.JsonElement;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.ComponentSerialization;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;

import java.util.Locale;
import java.util.Optional;

public class TextDebugger extends Module {
    @Setting
    public static final ListValue mode = new ListValue("Mode", new String[]{"Legacy", "JSON", "Plain"}, "Legacy");

    @Setting
    public static final BooleanValue chat = new BooleanValue("Chat", true);

    @Setting
    public static final BooleanValue tabList = new BooleanValue("Tab List", true);

    @Setting
    public static final BooleanValue scoreboard = new BooleanValue("Scoreboard", true);

    @Setting
    public static final BooleanValue nameTags = new BooleanValue("Name Tags", true);

    @Setting
    public static final BooleanValue containers = new BooleanValue("Containers", true);

    @Setting
    public static final BooleanValue serverList = new BooleanValue("Server List", true);

    private String lastMode;
    private boolean lastChat;

    public TextDebugger() {
        super("Text Debugger", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        lastMode = mode.get();
        lastChat = chat.get();
        refreshChat();
    }

    @Override
    public void onDisable() {
        refreshChat();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!mode.get().equals(lastMode) || chat.get() != lastChat) {
            lastMode = mode.get();
            lastChat = chat.get();
            refreshChat();
        }
    }

    private static void refreshChat() {
        Minecraft client = Minecraft.getInstance();
        if (client == null || client.gui == null || client.gui.hud == null)
            return;
        client.execute(() -> client.gui.hud.getChat().rescaleChat());
    }

    public static boolean chatActive() {
        return ModuleManager.getModuleState(TextDebugger.class) && chat.get();
    }

    public static boolean tabListActive() {
        return ModuleManager.getModuleState(TextDebugger.class) && tabList.get();
    }

    public static boolean scoreboardActive() {
        return ModuleManager.getModuleState(TextDebugger.class) && scoreboard.get();
    }

    public static boolean nameTagsActive() {
        return ModuleManager.getModuleState(TextDebugger.class) && nameTags.get();
    }

    public static boolean containersActive() {
        return ModuleManager.getModuleState(TextDebugger.class) && containers.get();
    }

    public static boolean serverListActive() {
        return ModuleManager.getModuleState(TextDebugger.class) && serverList.get();
    }

    public static String raw(String text) {
        return text == null ? null : escape(text);
    }

    public static FormattedText rawText(FormattedText text) {
        return text instanceof Component component ? raw(component) : text;
    }

    public static MutableComponent raw(Component component) {
        if (component == null)
            return null;
        String text = switch (mode.get()) {
            case "JSON" -> json(component);
            case "Plain" -> component.getString();
            default -> legacy(component);
        };
        return Component.literal(escape(text));
    }

    private static String escape(String text) {
        return text.replace(ChatFormatting.PREFIX_CODE, '&');
    }

    private static String json(Component component) {
        Minecraft client = Minecraft.getInstance();
        DynamicOps<JsonElement> ops = client != null && client.level != null
                ? client.level.registryAccess().createSerializationContext(JsonOps.INSTANCE)
                : JsonOps.INSTANCE;
        return ComponentSerialization.CODEC.encodeStart(ops, component)
                .result()
                .map(JsonElement::toString)
                .orElseGet(component::getString);
    }

    private static String legacy(Component component) {
        StringBuilder builder = new StringBuilder();
        Style[] previous = {Style.EMPTY};
        component.visit((style, content) -> {
            if (content.isEmpty())
                return Optional.empty();
            if (!formatEquals(style, previous[0])) {
                if (hasFormat(previous[0]))
                    builder.append("&r");
                appendFormat(builder, style);
                previous[0] = style;
            }
            builder.append(content);
            return Optional.empty();
        }, Style.EMPTY);
        return builder.toString();
    }

    private static boolean hasFormat(Style style) {
        return style.getColor() != null || style.isBold() || style.isItalic() || style.isUnderlined()
                || style.isStrikethrough() || style.isObfuscated();
    }

    private static boolean formatEquals(Style a, Style b) {
        return java.util.Objects.equals(a.getColor(), b.getColor())
                && a.isBold() == b.isBold()
                && a.isItalic() == b.isItalic()
                && a.isUnderlined() == b.isUnderlined()
                && a.isStrikethrough() == b.isStrikethrough()
                && a.isObfuscated() == b.isObfuscated();
    }

    private static void appendFormat(StringBuilder builder, Style style) {
        TextColor color = style.getColor();
        if (color != null)
            builder.append(colorCode(color));
        if (style.isObfuscated())
            builder.append("&k");
        if (style.isBold())
            builder.append("&l");
        if (style.isStrikethrough())
            builder.append("&m");
        if (style.isUnderlined())
            builder.append("&n");
        if (style.isItalic())
            builder.append("&o");
    }

    private static String colorCode(TextColor color) {
        String name = color.serialize();
        if (!name.startsWith("#")) {
            try {
                return "&" + ChatFormatting.valueOf(name.toUpperCase(Locale.ROOT)).toString().charAt(1);
            } catch (IllegalArgumentException ignored) {
            }
        }
        return "&" + color.formatValue();
    }
}
