package com.instrumentalist.mixin.injector;

import com.instrumentalist.mixin.oringo.IEntityRenderState;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.entity.EntityRenderDispatcher;
import net.minecraft.client.renderer.entity.state.EntityRenderState;
import net.minecraft.client.renderer.state.level.CameraRenderState;
import net.minecraft.world.entity.Entity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EntityRenderDispatcher.class)
public abstract class EntityRenderDispatcherMixin {

    @ModifyReturnValue(method = "extractEntity", at = @At("RETURN"))
    private <E extends Entity, S extends EntityRenderState> S render$extractEntity(S state, E entity, float tickDelta) {
        IEntityRenderState krsState = (IEntityRenderState) state;
        krsState.client$setEntity(entity);
        return state;
    }

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private <S extends EntityRenderState> void krs$submitPre(S state, CameraRenderState cameraState, double x, double y, double z, PoseStack matrices, SubmitNodeCollector submitter, CallbackInfo ci) {
        if (state.leashStates != null && !state.leashStates.isEmpty()) {
            ci.cancel();
        }
    }

    @Inject(method = "shouldRender", at = @At("HEAD"), cancellable = true)
    private <E extends Entity> void krs$skipNullEntity(E entity, Frustum culler, double camX, double camY, double camZ, float partialTicks, CallbackInfoReturnable<Boolean> cir) {
        if (entity == null) {
            cir.setReturnValue(false);
        }
    }
}
