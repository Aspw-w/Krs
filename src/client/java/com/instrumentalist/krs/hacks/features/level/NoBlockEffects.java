package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.level.block.Blocks;

public class NoBlockEffects extends Module {

    public NoBlockEffects() {
        super("No Block Effects", ModuleCategory.Level, Keys.KEY_UNKNOWN, false, true);
    }

    public static boolean shouldIgnore(Object entity) {
        return entity instanceof LocalPlayer && ModuleManager.getModuleState(NoBlockEffects.class);
    }

    public static float normalFriction() {
        return Blocks.STONE.getFriction();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }
}
