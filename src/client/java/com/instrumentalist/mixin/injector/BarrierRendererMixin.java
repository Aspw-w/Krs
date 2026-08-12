package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.TrueSight;
import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.player.AbstractClientPlayer;
import net.minecraft.client.renderer.ItemInHandRenderer;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.core.BlockPos;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemDisplayContext;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ItemInHandRenderer.class)
public abstract class BarrierRendererMixin {

    @Shadow @Final private Minecraft minecraft;

    @Shadow public abstract void renderItem(LivingEntity entity, ItemStack stack, ItemDisplayContext renderMode, PoseStack matrices, SubmitNodeCollector submitter, int light);

    @Unique
    private static ItemStack barrierStack;

    @Unique
    private static ItemStack getBarrierStack() {
        if (barrierStack == null) {
            barrierStack = new ItemStack(Blocks.BARRIER.asItem());
        }
        return barrierStack;
    }

    @Inject(method = "submitArmWithItem", at = @At("RETURN"))
    private void renderBarrierBlock(AbstractClientPlayer player, float tickDelta, float pitch, InteractionHand hand,
                                    float swingProgress, ItemStack stack, float equipProgress,
                                    PoseStack matrices, SubmitNodeCollector submitter, int light, CallbackInfo ci) {
        if (!ModuleManager.getModuleState(TrueSight.class) || !TrueSight.barriers.get()) {
            return;
        }

        if (!(minecraft.hitResult instanceof BlockHitResult blockHitResult)) {
            return;
        }

        if (blockHitResult.getType() != HitResult.Type.BLOCK) {
            return;
        }

        BlockPos pos = blockHitResult.getBlockPos();
        if (!player.level().getBlockState(pos).is(Blocks.BARRIER)) {
            return;
        }

        LivingEntity livingEntity = (LivingEntity) player;
        ItemDisplayContext context = hand == InteractionHand.MAIN_HAND
            ? ItemDisplayContext.FIRST_PERSON_RIGHT_HAND
            : ItemDisplayContext.FIRST_PERSON_LEFT_HAND;

        renderItem(livingEntity, getBarrierStack(), context, matrices, submitter, light);
    }
}
