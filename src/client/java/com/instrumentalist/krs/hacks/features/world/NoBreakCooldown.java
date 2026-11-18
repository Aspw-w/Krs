package com.instrumentalist.krs.hacks.features.world;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.input.Keys;

public class NoBreakCooldown extends Module {

    public NoBreakCooldown() {
        super("No Break Cooldown", ModuleCategory.World, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }
}
