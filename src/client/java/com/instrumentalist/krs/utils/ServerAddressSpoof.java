package com.instrumentalist.krs.utils;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.world.RewoundHider;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.util.ArrayList;
import java.util.IdentityHashMap;
import java.util.Map;
import java.util.List;
import java.util.Locale;

public final class ServerAddressSpoof {
    private static final String SCOREBOARD_FROM = "play.rewound.cc";
    private static final String SCOREBOARD_TO = "www.hypixel.net";
    private static final String INTERFACE_TO = "mc.hypixel.net";

    private ServerAddressSpoof() {
    }

    private static boolean enabled() {
        return ModuleManager.getModuleState(RewoundHider.class);
    }

    public static String interfaceAddress(String address) {
        if (address == null || !enabled())
            return address;
        String trimmed = address.trim();
        int portIndex = trimmed.lastIndexOf(':');
        String host = portIndex >= 0 ? trimmed.substring(0, portIndex) : trimmed;
        String port = portIndex >= 0 ? trimmed.substring(portIndex) : "";
        if (host.endsWith("."))
            host = host.substring(0, host.length() - 1);
        String lower = host.toLowerCase(Locale.ROOT);
        if (lower.equals("play.rewound.cc") || lower.equals("rewound.cc"))
            return INTERFACE_TO + port;
        return address;
    }

    public static Component scoreboard(Component component) {
        if (!enabled())
            return component;
        return replace(replace(component, SCOREBOARD_FROM, SCOREBOARD_TO), "rewound", "Hypixel");
    }

    public static Component tabList(Component component) {
        if (!enabled())
            return component;
        return replace(replace(component, "play.rewound.cc", "mc.hypixel.net"), "www.rewound.cc", "store.hypixel.net");
    }

    public static Component bossBar(Component component) {
        if (!enabled())
            return component;
        return replace(component, "play.rewound.cc", "mc.hypixel.net");
    }

    public static Component chat(Component component) {
        if (!enabled())
            return component;
        return replaceDeep(replaceDeep(component, "rewound level", "Hypixel Level", 0),
                "rewound experience", "Hypixel Experience", 0);
    }

    private static Component replaceDeep(Component component, String from, String to, int depth) {
        if (component == null || depth > 4)
            return component;

        List<Integer> codePoints = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        component.getVisualOrderText().accept((index, style, codePoint) -> {
            codePoints.add(codePoint);
            styles.add(style);
            return true;
        });

        boolean changed = false;
        Map<Style, Style> mappedStyles = new IdentityHashMap<>();
        for (int i = 0; i < styles.size(); i++) {
            Style original = styles.get(i);
            Style mapped = mappedStyles.computeIfAbsent(original, style -> mapHover(style, from, to, depth));
            if (mapped != original) {
                styles.set(i, mapped);
                changed = true;
            }
        }

        StringBuilder builder = new StringBuilder(codePoints.size());
        for (int codePoint : codePoints)
            builder.appendCodePoint(codePoint);
        String source = builder.toString();
        String text = source.toLowerCase(Locale.ROOT);
        if (!changed && !text.contains(from))
            return component;
        return rebuild(codePoints, styles, source, text, from, to);
    }

    private static Style mapHover(Style style, String from, String to, int depth) {
        if (style.getHoverEvent() instanceof HoverEvent.ShowText(Component value)) {
            Component replaced = replaceDeep(value, from, to, depth + 1);
            if (replaced != value)
                return style.withHoverEvent(new HoverEvent.ShowText(replaced));
        }
        return style;
    }

    private static Component replace(Component component, String from, String to) {
        if (component == null)
            return null;

        List<Integer> codePoints = new ArrayList<>();
        List<Style> styles = new ArrayList<>();
        component.getVisualOrderText().accept((index, style, codePoint) -> {
            codePoints.add(codePoint);
            styles.add(style);
            return true;
        });

        StringBuilder builder = new StringBuilder(codePoints.size());
        for (int codePoint : codePoints)
            builder.appendCodePoint(codePoint);
        String source = builder.toString();
        String text = source.toLowerCase(Locale.ROOT);
        if (!text.contains(from))
            return component;
        return rebuild(codePoints, styles, source, text, from, to);
    }

    private static Component rebuild(List<Integer> codePoints, List<Style> styles, String source, String text, String from, String to) {
        MutableComponent result = Component.empty();
        StringBuilder run = new StringBuilder();
        Style runStyle = null;
        int fromLength = from.length();
        int charIndex = 0;
        for (int i = 0; i < codePoints.size(); ) {
            if (text.startsWith(from, charIndex)) {
                if (!run.isEmpty())
                    result.append(Component.literal(run.toString()).withStyle(runStyle));
                run.setLength(0);
                runStyle = null;
                String matched = source.substring(charIndex, charIndex + fromLength);
                String replacement = matched.equals(matched.toUpperCase(Locale.ROOT)) ? to.toUpperCase(Locale.ROOT) : to;
                result.append(Component.literal(replacement).withStyle(styles.get(i)));
                for (int skipped = 0; skipped < fromLength && i < codePoints.size(); skipped++) {
                    charIndex += Character.charCount(codePoints.get(i));
                    i++;
                }
                continue;
            }

            Style style = styles.get(i);
            if (runStyle != null && !runStyle.equals(style)) {
                result.append(Component.literal(run.toString()).withStyle(runStyle));
                run.setLength(0);
            }
            runStyle = style;
            run.appendCodePoint(codePoints.get(i));
            charIndex += Character.charCount(codePoints.get(i));
            i++;
        }
        if (!run.isEmpty())
            result.append(Component.literal(run.toString()).withStyle(runStyle));
        return result;
    }
}
