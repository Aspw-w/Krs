package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.input.Keys;

public class CameraNoClip extends Module {

    @Setting
    public static final FloatValue distance = new FloatValue("Distance", 0f, -4f, 100f, "m");

    public CameraNoClip() {
        super("Camera No Clip", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

}
