package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.input.Keys;

public class AntiBlind extends Module {

    @Setting
    public static final BooleanValue fire = new BooleanValue("Fire", true);

    @Setting
    public static final BooleanValue pumpkin = new BooleanValue("Pumpkin", true);

    @Setting
    public static final BooleanValue camera = new BooleanValue("Camera", true);

    @Setting
    public static final BooleanValue effects = new BooleanValue("Effects", true);

    public AntiBlind() {
        super("Anti Blind", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

}
