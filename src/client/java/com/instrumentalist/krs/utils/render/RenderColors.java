package com.instrumentalist.krs.utils.render;

import java.awt.Color;

public final class RenderColors {
    public static final Color DEFAULT = Color.WHITE;

    public static final Color LINE = NanoVGTheme.ACCENT;
    public static final Color LINE_SECONDARY = NanoVGTheme.MUTED;

    public static final Color KILL_AURA_PREDICTION = LINE_SECONDARY;
    public static final Color KILL_AURA_TELEPORT = LINE;
    public static final Color TARGET_STRAFE = LINE;
    public static final Color BACKTRACK_TARGET = LINE;
    public static final Color BACKTRACK_SELF = LINE_SECONDARY;
    public static final Color PATH_FINDER = LINE;
    public static final Color LOOK_TP = LINE;
    public static final Color STALKER = LINE;
    public static final Color CHEST_TP = LINE;
    public static final Color SURROUND_TP = LINE;

    public static final Color SCAFFOLD = LINE;
    public static final Color AIR_PLACE = LINE;
    public static final Color CIV_BREAK = LINE;
    public static final Color BREAKER = LINE;
    public static final Color NUKER = LINE;
    public static final Color ANTI_VOID = LINE;

    public static final Color VEIN_MINER = LINE;
    public static final Color TUNNEL_MINER = LINE;
    public static final Color PACKET_MINE = LINE;
    public static final Color GHOST_HAND = LINE;
    public static final Color HAMMER = LINE;
    public static final Color HARVESTER = LINE;
    public static final Color AUTO_MINE = LINE;
    public static final Color CITY_BREAKER = LINE;
    public static final Color BREAK_ESP = LINE;
    public static final Color BOW_AIMBOT = LINE;
    public static final Color CRYSTAL_AURA = LINE;

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
    }

    private RenderColors() {
    }
}
