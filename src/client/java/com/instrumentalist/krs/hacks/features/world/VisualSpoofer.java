package com.instrumentalist.krs.hacks.features.world;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;

public class VisualSpoofer extends Module {

    public VisualSpoofer() {
        super("Visual Spoofer", ModuleCategory.World, Keys.KEY_UNKNOWN, true, false);
    }

    @Override
    public void onDisable() {
        PlayerUtil.INSTANCE.fullResetSpoofState();
    }

    @Override
    public void onEnable() {
    }
}
