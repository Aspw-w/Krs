package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.input.Keys;

public class NoHurtCam extends Module {
    public NoHurtCam() {
        super("No Hurt Cam", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }
}
