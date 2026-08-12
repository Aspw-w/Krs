package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

public class TrueSight extends Module {

    @Setting
    public static final BooleanValue entities = new BooleanValue("Entities", true);

    @Setting
    public static final BooleanValue barriers = new BooleanValue("Barriers", false);

    @Setting
    public static final FloatValue entityAlpha = new FloatValue("EntityAlpha", 50, 0, 100);

    public TrueSight() {
        super("TrueSight", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, false, false);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    public static boolean shouldRenderEntity(net.minecraft.world.entity.LivingEntity entity) {
        if (!ModuleManager.getModuleState(TrueSight.class)) {
            return false;
        }

        if (!entities.get()) {
            return false;
        }

        return entity.isInvisible();
    }

    public static boolean requiresTrueSight(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity livingEntity)) {
            return false;
        }

        return livingEntity.isInvisible();
    }
}
