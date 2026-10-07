package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.ClientCape;
import com.instrumentalist.krs.utils.render.GuiEntityRenderGuard;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import net.minecraft.client.model.player.PlayerCapeModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(PlayerCapeModel.class)
public abstract class PlayerCapeModelMixin extends PlayerModel {
    public PlayerCapeModelMixin(ModelPart root, boolean slim) {
        super(root, slim);
    }

    @Inject(method = "setupAnim(Lnet/minecraft/client/renderer/entity/state/AvatarRenderState;)V", at = @At("TAIL"))
    private void krs$removeSwingCapeYaw(AvatarRenderState state, CallbackInfo ci) {
        if (!GuiEntityRenderGuard.isActive()
                && state instanceof IEntityRenderState renderState
                && renderState.client$getEntity() instanceof LocalPlayer
                && ModuleManager.getModuleState(ClientCape.class)
                && ClientCape.oldCapeMovement.get()) {
            this.body.yRot = 0.0F;
        }
    }
}
