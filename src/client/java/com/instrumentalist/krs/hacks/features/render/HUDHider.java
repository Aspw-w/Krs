package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.input.Keys;

public class HUDHider extends Module {

    public HUDHider() {
        super("Hud Hider", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String description() {
        return "Hides hud elements";
    }

    @Setting
    public static final BooleanValue board = new BooleanValue("Scoreboard", true);

    @Setting
    public static final BooleanValue bos = new BooleanValue("Boss Bar", true);

    @Setting
    public static final BooleanValue bar = new BooleanValue("Action Bar", true);

    @Setting
    public static final BooleanValue titled = new BooleanValue("Title", true);

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }
}
