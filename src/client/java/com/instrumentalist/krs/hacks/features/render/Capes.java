package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.input.Keys;

public class Capes extends Module {

    @Setting
    public static final BooleanValue customCape = new BooleanValue("Custom Cape", false);

    @Setting
    public static final BooleanValue capeOverride = new BooleanValue("Cape Override", true, customCape::get);

    @Setting
    public static final BooleanValue enchantmentGlint = new BooleanValue("Enchantment Glint", true);

    @Setting
    public static final BooleanValue oldCapeMovement = new BooleanValue("1.8 Cape Movement", true);

    public Capes() {
        super("Capes", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

}
