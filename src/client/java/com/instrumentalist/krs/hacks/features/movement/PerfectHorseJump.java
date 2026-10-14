package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;

public class PerfectHorseJump extends Module {

    public PerfectHorseJump() {
        super("Perfect Horse Jump", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    public static float modifiedHorseJump(float original) {
        if (ModuleManager.getModuleState(PerfectHorseJump.class))
            return 1f;

        return original;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }
}
