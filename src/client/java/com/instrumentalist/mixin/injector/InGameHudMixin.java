package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.RenderHudEvent;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.AntiBlind;
import com.instrumentalist.krs.hacks.features.render.Interface;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Collection;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Hud;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.renderer.RenderPipelines;
import net.minecraft.core.Holder;
import net.minecraft.resources.Identifier;
import net.minecraft.util.ARGB;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;

@Mixin(Hud.class)
public abstract class InGameHudMixin implements IMinecraft {

    @Shadow @Final private Minecraft minecraft;

    @Shadow @Final private static Identifier EFFECT_BACKGROUND_AMBIENT_SPRITE;

    @Shadow @Final private static Identifier EFFECT_BACKGROUND_SPRITE;

    @Unique private List<MobEffectInstance> krs$sortedEffects;
    @Unique private Identifier[] krs$effectSprites;
    @Unique private int[] krs$effectIconX;
    @Unique private int[] krs$effectIconY;
    @Unique private float[] krs$effectIconAlpha;
    @Unique private static final Comparator<MobEffectInstance> KRS_EFFECT_ORDER = Comparator.reverseOrder();

    @Inject(method = "extractRenderState", at = @At(value = "HEAD"))
    public void renderHud(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (Client.eventManager == null || !Client.eventManager.hasListeners(RenderHudEvent.class))
            return;

        RenderHudEvent event = new RenderHudEvent(tickCounter.getGameTimeDeltaPartialTick(true));
        Client.eventManager.call(event);
    }

    @Inject(at = @At("HEAD"), method = "extractTextureOverlay(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/resources/Identifier;F)V", cancellable = true)
    private void antiBlindHook(GuiGraphicsExtractor context, Identifier texture, float opacity, CallbackInfo ci) {
        if (ModuleManager.getModuleState(AntiBlind.class) && AntiBlind.pumpkin.get())
            ci.cancel();
    }

    @Inject(method = "extractEffects", at = @At("HEAD"), cancellable = true)
    private void disableStatusEffectOverlay(GuiGraphicsExtractor context, DeltaTracker tickCounter, CallbackInfo ci) {
        if (ModuleManager.getModuleState(Interface.class)) {
            ci.cancel();
            if (!Interface.someInformation.get()) {
                context.pose().pushMatrix();
                try {
                    context.pose().translate(0.0F, mc.getWindow().getGuiScaledHeight() - 26.0F);
                    this.customRenderStatusEffectOverlay(context, tickCounter);
                } finally {
                    context.pose().popMatrix();
                }
            }
        }
    }

    @Unique
    private void customRenderStatusEffectOverlay(GuiGraphicsExtractor context, DeltaTracker tickCounter) {
        if (this.minecraft.player == null)
            return;

        Collection<MobEffectInstance> collection = this.minecraft.player.getActiveEffects();
        if (krs$sortedEffects == null) krs$sortedEffects = new ArrayList<>();
        if (krs$effectSprites == null) krs$effectSprites = new Identifier[8];
        if (krs$effectIconX == null) krs$effectIconX = new int[8];
        if (krs$effectIconY == null) krs$effectIconY = new int[8];
        if (krs$effectIconAlpha == null) krs$effectIconAlpha = new float[8];
        krs$sortedEffects.clear();
        if (!collection.isEmpty() && (this.minecraft.gui.screen() == null || !this.minecraft.gui.screen().showsActiveEffects())) {
            int i = 0;
            int j = 0;
            int iconCount = 0;

            krs$sortedEffects.addAll(collection);
            krs$sortedEffects.sort(KRS_EFFECT_ORDER);

            for (MobEffectInstance statusEffectInstance : krs$sortedEffects) {
                Holder<MobEffect> registryEntry = statusEffectInstance.getEffect();
                if (statusEffectInstance.showIcon()) {
                    int k = context.guiWidth();
                    int l = 1;

                    if (((MobEffect)registryEntry.value()).isBeneficial()) {
                        ++i;
                        k -= 25 * i;
                    } else {
                        ++j;
                        k -= 25 * j;
                        l -= 26;
                    }

                    float f = 1.0F;
                    if (statusEffectInstance.isAmbient()) {
                        context.blitSprite(RenderPipelines.GUI_TEXTURED, EFFECT_BACKGROUND_AMBIENT_SPRITE, k, l, 24, 24);
                    } else {
                        context.blitSprite(RenderPipelines.GUI_TEXTURED, EFFECT_BACKGROUND_SPRITE, k, l, 24, 24);
                        if (statusEffectInstance.endsWithin(200)) {
                            int m = statusEffectInstance.getDuration();
                            int n = 10 - m / 20;
                            f = Mth.clamp((float)m / 10.0F / 5.0F * 0.5F, 0.0F, 0.5F) + Mth.cos((float)m * (float)Math.PI / 5.0F) * Mth.clamp((float)n / 10.0F * 0.25F, 0.0F, 0.25F);
                            f = Mth.clamp(f, 0.0F, 1.0F);
                        }
                    }

                    Identifier sprite = Hud.getMobEffectSprite(registryEntry);
                    krs$ensureEffectIconCapacity(iconCount + 1);
                    krs$effectSprites[iconCount] = sprite;
                    krs$effectIconX[iconCount] = k + 3;
                    krs$effectIconY[iconCount] = l + 3;
                    krs$effectIconAlpha[iconCount] = f;
                    iconCount++;
                }
            }

            try {
                for (int index = 0; index < iconCount; index++) {
                    int color = ARGB.white(krs$effectIconAlpha[index]);
                    context.blitSprite(RenderPipelines.GUI_TEXTURED, krs$effectSprites[index], krs$effectIconX[index], krs$effectIconY[index], 18, 18, color);
                }
            } finally {
                Arrays.fill(krs$effectSprites, 0, iconCount, null);
            }
        }
    }

    @Unique
    private void krs$ensureEffectIconCapacity(int requiredCapacity) {
        if (requiredCapacity <= krs$effectSprites.length)
            return;

        int newCapacity = Math.max(requiredCapacity, krs$effectSprites.length * 2);
        krs$effectSprites = Arrays.copyOf(krs$effectSprites, newCapacity);
        krs$effectIconX = Arrays.copyOf(krs$effectIconX, newCapacity);
        krs$effectIconY = Arrays.copyOf(krs$effectIconY, newCapacity);
        krs$effectIconAlpha = Arrays.copyOf(krs$effectIconAlpha, newCapacity);
    }
}
