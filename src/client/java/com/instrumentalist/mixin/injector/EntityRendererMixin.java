package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.dev.TextDebugger;
import com.instrumentalist.krs.hacks.features.render.NameTags;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(EntityRenderer.class)
public abstract class EntityRendererMixin<T extends Entity, S extends EntityRenderState> {

    @Inject(method = "extractRenderState(Lnet/minecraft/world/entity/Entity;Lnet/minecraft/client/renderer/entity/state/EntityRenderState;F)V", at = @At("TAIL"))
    private void krs$hideVanillaNameTag(T entity, S state, float tickDelta, CallbackInfo ci) {
        ((IEntityRenderState) state).client$setEntity(entity);

        if (ModuleManager.getModuleState(NameTags.class) && NameTags.shouldRender(entity)) {
            state.nameTag = null;
            state.scoreText = null;
        }

        if (TextDebugger.nameTagsActive()) {
            if (state.nameTag != null)
                state.nameTag = TextDebugger.raw(state.nameTag);
            if (state.scoreText != null)
                state.scoreText = TextDebugger.raw(state.scoreText);
        }
    }

    @Inject(at = @At("HEAD"), method = "submit", cancellable = true)
    public void render(S state, PoseStack matrices, SubmitNodeCollector submitter, CameraRenderState cameraState, CallbackInfo ci) {
        if (state.leashStates != null && !state.leashStates.isEmpty()) {
            ci.cancel();
            return;
        }

    }
}
