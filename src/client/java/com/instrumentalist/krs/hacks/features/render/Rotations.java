package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.input.Keys;

public class Rotations extends Module {
    @Setting
    public static final BooleanValue realistic = new BooleanValue("Realistic", false);

    @Setting
    public static final BooleanValue capeFix = new BooleanValue("Cape Fix", false);

    public Rotations() {
        super("Rotations", ModuleCategory.Render, Keys.KEY_UNKNOWN, true, false);
    }

    public static boolean shouldUseVanilla() {
        return ModuleManager.getModuleState(Rotations.class) && realistic.get();
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }
}
