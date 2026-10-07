package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import org.lwjgl.glfw.GLFW;

public class Reach extends Module {

    public Reach() {
        super("Reach", ModuleCategory.Combat, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Setting
    private static final BooleanValue block = new BooleanValue("Block", true);

    @Setting
    private static final FloatValue blockReach = new FloatValue("Block Reach", 6f, 0.1f, 6f, "m", block::get);

    @Setting
    private static final BooleanValue entity = new BooleanValue("Entity", true);

    @Setting
    private static final FloatValue entityReach = new FloatValue("Entity Reach", 6f, 0.1f, 6f, "m", entity::get);

    public static double hookBlockReach(double original) {
        if (ModuleManager.getModuleState(Reach.class) && block.get())
            return blockReach.get();

        return original;
    }

    public static double hookEntityReach(double original) {
        if (ModuleManager.getModuleState(Reach.class) && entity.get())
            return entityReach.get();

        return original;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }
}