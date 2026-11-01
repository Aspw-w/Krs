package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.input.Keys;

public class ServerRotations extends Module {
    @Setting
    public static final BooleanValue vanillaBody = new BooleanValue("Vanilla Body", true);

    @Setting
    public static final BooleanValue capeMovementCorrection = new BooleanValue("Cape Movement Correction", true);

    public ServerRotations() {
        super("Server Rotations", ModuleCategory.Render, Keys.KEY_UNKNOWN, true, false);
    }

    public static boolean shouldUseVanilla() {
        return ModuleManager.getModuleState(ServerRotations.class) && vanillaBody.get();
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }
}
