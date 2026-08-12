--- src/client/java/com/instrumentalist/krs/hacks/features/render/TrueSight.java (原始)


+++ src/client/java/com/instrumentalist/krs/hacks/features/render/TrueSight.java (修改后)
package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import org.lwjgl.glfw.GLFW;

import java.awt.*;

/**
 * TrueSight module
 * <p>
 * Allows you to see invisible entities and barriers.
 */
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

    /**
     * Check if an entity should be rendered with TrueSight
     *
     * @param entity the entity to check
     * @return true if the entity should be rendered
     */
    public static boolean shouldRenderEntity(net.minecraft.world.entity.LivingEntity entity) {
        if (!ModuleManager.getModuleState(TrueSight.class)) {
            return false;
        }

        if (!entities.get()) {
            return false;
        }

        return entity.isInvisible();
    }

    /**
     * Check if ESP module requires TrueSight for this entity
     *
     * @param entity the entity to check
     * @return true if ESP needs TrueSight for this entity
     */
    public static boolean requiresTrueSight(net.minecraft.world.entity.Entity entity) {
        if (!(entity instanceof net.minecraft.world.entity.LivingEntity livingEntity)) {
            return false;
        }

        return livingEntity.isInvisible();
    }
}