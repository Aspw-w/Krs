package com.instrumentalist.krs.hacks.features.player;



import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class SneakSpam extends Module {

    public SneakSpam() {
        super("Sneak Spam", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (mc.player == null || InputConstants.isKeyDown(mc.getWindow(), InputConstants.getKey(mc.options.keyShift.saveString()).getValue())) return;

        mc.options.keyShift.setDown(!mc.options.keyShift.isDown());
    }
}
