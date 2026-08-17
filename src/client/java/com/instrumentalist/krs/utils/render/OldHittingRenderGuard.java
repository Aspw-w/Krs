package com.instrumentalist.krs.utils.render;

import com.instrumentalist.krs.hacks.features.render.OldHitting;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.Minecraft;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

import java.lang.reflect.Method;

public final class OldHittingRenderGuard {

    private static final ThreadLocal<Entity> RENDERED_ENTITY = new ThreadLocal<>();
    private static volatile boolean emfLookupComplete;
    private static Method emfCurrentEntityGetter;

    private OldHittingRenderGuard() {
    }

    public static Entity enter(Entity entity) {
        Entity previous = RENDERED_ENTITY.get();

        if (entity == null)
            RENDERED_ENTITY.remove();
        else
            RENDERED_ENTITY.set(entity);

        return previous;
    }

    public static void exit(Entity previous) {
        if (previous == null)
            RENDERED_ENTITY.remove();
        else
            RENDERED_ENTITY.set(previous);
    }

    public static boolean shouldSpoofUseState(LivingEntity entity) {
        Minecraft minecraft = Minecraft.getInstance();
        if (entity != minecraft.player
                || minecraft.options.getCameraType().isFirstPerson()
                || GuiEntityRenderGuard.isActive()
                || !OldHitting.shouldBlock())
            return false;

        return entity == RENDERED_ENTITY.get() || isCurrentEmfEntity(entity);
    }

    private static boolean isCurrentEmfEntity(LivingEntity entity) {
        Method getter = getEmfCurrentEntityGetter();
        if (getter == null)
            return false;

        try {
            return getter.invoke(null) == entity;
        } catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private static Method getEmfCurrentEntityGetter() {
        if (emfLookupComplete)
            return emfCurrentEntityGetter;

        synchronized (OldHittingRenderGuard.class) {
            if (emfLookupComplete)
                return emfCurrentEntityGetter;

            if (FabricLoader.getInstance().isModLoaded("entity_model_features")) {
                try {
                    Class<?> contextClass = Class.forName(
                            "traben.entity_model_features.models.animation.EMFAnimationEntityContext",
                            false,
                            OldHittingRenderGuard.class.getClassLoader()
                    );
                    emfCurrentEntityGetter = contextClass.getMethod("getEMFEntity");
                } catch (ReflectiveOperationException ignored) {
                    emfCurrentEntityGetter = null;
                }
            }

            emfLookupComplete = true;
            return emfCurrentEntityGetter;
        }
    }
}
