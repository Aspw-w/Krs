package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.level.CaveFinder;
import com.instrumentalist.krs.hacks.features.level.Xray;
import com.instrumentalist.krs.utils.input.Keys;

public class FullBright extends Module {
    public FullBright() {
        super("Full Bright", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    public static boolean isBrightening() {
        return ModuleManager.getModuleState(Xray.class) || ModuleManager.getModuleState(CaveFinder.class) || ModuleManager.getModuleState(FullBright.class);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }
}
