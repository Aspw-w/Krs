package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.ClientCape;
import com.instrumentalist.krs.utils.render.GuiEntityRenderGuard;
import com.instrumentalist.mixin.oringo.IEntityRenderState;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.model.player.PlayerModel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.RenderLayerParent;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.layers.RenderLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.world.entity.player.PlayerSkin;
import net.minecraft.client.resources.model.EquipmentClientInfo;
import net.minecraft.world.item.ItemStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(CapeLayer.class)
public abstract class CapeFeatureRendererMixin extends RenderLayer<AvatarRenderState, PlayerModel> {

    @Shadow
    private boolean hasLayer(ItemStack stack, EquipmentClientInfo.LayerType layerType) {
        throw new AssertionError();
    }

    @Shadow private HumanoidModel<AvatarRenderState> model;

    public CapeFeatureRendererMixin(RenderLayerParent<AvatarRenderState, PlayerModel> context) {
        super(context);
    }

    @Inject(method = "submit(Lcom/mojang/blaze3d/vertex/PoseStack;Lnet/minecraft/client/renderer/SubmitNodeCollector;ILnet/minecraft/client/renderer/entity/state/AvatarRenderState;FF)V", at = @At("HEAD"), cancellable = true)
    public void render(PoseStack matrixStack, SubmitNodeCollector submitter, int i, AvatarRenderState playerEntityRenderState, float f, float g, CallbackInfo ci) {
        if (!GuiEntityRenderGuard.isActive()
                && ((IEntityRenderState) playerEntityRenderState).client$getEntity() instanceof LocalPlayer
                && ModuleManager.getModuleState(ClientCape.class)) {
            ci.cancel();

            if (!playerEntityRenderState.isInvisible && playerEntityRenderState.showCape) {
                PlayerSkin skinTextures = playerEntityRenderState.skin;
                if (skinTextures.cape() != null) {
                    if (!this.hasLayer(playerEntityRenderState.chestEquipment, EquipmentClientInfo.LayerType.WINGS)) {
                        matrixStack.pushPose();
                        if (this.hasLayer(playerEntityRenderState.chestEquipment, EquipmentClientInfo.LayerType.HUMANOID)) {
                            matrixStack.translate(0.0F, -0.053125F, 0.06875F);
                        }

                        if (ClientCape.enchantmentGlint.get())
                            submitter.submitModel(this.model, playerEntityRenderState, matrixStack, RenderTypes.entityGlint(), i, OverlayTexture.NO_OVERLAY, playerEntityRenderState.outlineColor, null);

                        submitter.submitModel(this.model, playerEntityRenderState, matrixStack, RenderTypes.entitySolid(skinTextures.cape().texturePath()), i, OverlayTexture.NO_OVERLAY, playerEntityRenderState.outlineColor, null);
                        matrixStack.popPose();
                    }
                }
            }
        }
    }
}
