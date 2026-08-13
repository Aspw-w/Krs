package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.BooleanValue;
import org.lwjgl.glfw.GLFW;

public class TrueSight extends Module {

    @Setting
    public static final BooleanValue entities = new BooleanValue("Entities", false);

    @Setting
    public static final BooleanValue barriers = new BooleanValue("Barriers", true);

    public TrueSight() {
        super("True Sight", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    public static boolean requiresTrueSight(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity livingEntity)) {
            return false;
        }

        return livingEntity.isInvisible();
    }
}
