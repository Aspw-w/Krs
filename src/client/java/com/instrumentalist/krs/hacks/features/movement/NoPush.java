package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.input.Keys;

public class NoPush extends Module {

    public NoPush() {
        super("No Push", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }
}
