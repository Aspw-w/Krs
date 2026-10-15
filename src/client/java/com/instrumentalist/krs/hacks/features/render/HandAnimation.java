package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import org.lwjgl.glfw.GLFW;

public class HandAnimation extends Module{
    public HandAnimation() {
        super("Hand Animation", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Setting
    public static final FloatValue walkAnimationMultiplier = new FloatValue("Walk anim multiplier", 1.8f, 1.0f, 3.0f);

    @Setting
    public static final FloatValue shoulderSpread = new FloatValue("Shoulder spread", -0.16f, -0.5f, 0.5f);

    @Setting
    public static final FloatValue shoulderSweep = new FloatValue("Shoulder sweep", 0.4f, 0.0f, 1.0f);

    @Setting
    public static final BooleanValue figureEight = new BooleanValue("8 sweep", true);

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }
}
