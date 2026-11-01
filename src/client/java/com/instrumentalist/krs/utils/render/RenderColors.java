package com.instrumentalist.krs.utils.render;

import java.awt.Color;

public final class RenderColors {
    public static final Color DEFAULT = Color.WHITE;

    public static final Color KILL_AURA_PREDICTION = new Color(255, 170, 60);
    public static final Color KILL_AURA_TELEPORT = new Color(255, 85, 85);
    public static final Color TARGET_STRAFE = new Color(255, 100, 220);
    public static final Color BACKTRACK_TARGET = new Color(80, 225, 255);
    public static final Color BACKTRACK_SELF = new Color(150, 165, 255);
    public static final Color PATH_FINDER = new Color(100, 240, 140);
    public static final Color LOOK_TP = new Color(240, 240, 100);
    public static final Color STALKER = new Color(80, 255, 210);
    public static final Color CHEST_TP = new Color(255, 200, 80);
    public static final Color SURROUND_TP = new Color(175, 125, 255);

    public static final Color SCAFFOLD = new Color(90, 190, 255);
    public static final Color AIR_PLACE = new Color(200, 130, 255);
    public static final Color CIV_BREAK = new Color(255, 80, 80);
    public static final Color BREAKER = new Color(255, 150, 50);
    public static final Color NUKER = new Color(255, 225, 70);
    public static final Color ANTI_VOID = new Color(90, 255, 130);

    public static final Color VEIN_MINER = new Color(255, 110, 180);
    public static final Color TUNNEL_MINER = new Color(110, 150, 255);
    public static final Color PACKET_MINE = new Color(235, 95, 255);
    public static final Color GHOST_HAND = new Color(165, 255, 70);
    public static final Color HAMMER = new Color(255, 150, 90);
    public static final Color HARVESTER = new Color(120, 230, 90);
    public static final Color AUTO_MINE = new Color(90, 220, 255);
    public static final Color CITY_BREAKER = new Color(255, 70, 110);
    public static final Color BREAK_ESP = new Color(255, 185, 40);
    public static final Color BOW_AIMBOT = new Color(255, 230, 120);
    public static final Color CRYSTAL_AURA = new Color(200, 90, 255);

    public static Color withAlpha(Color color, int alpha) {
        return new Color(color.getRed(), color.getGreen(), color.getBlue(), Math.clamp(alpha, 0, 255));
    }

    private RenderColors() {
    }
}
