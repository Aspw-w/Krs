package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.ServerAddressSpoof;
import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.BossHealthOverlay;
import net.minecraft.client.gui.components.LerpingBossEvent;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyVariable;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Map;
import java.util.UUID;

@Mixin(BossHealthOverlay.class)
public class BossHealthOverlayMixin {

    @Shadow
    @Final
    private Map<UUID, LerpingBossEvent> events;

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void krs$styledBossBars(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (events.isEmpty() || !Interface.styleBossBar())
            return;
        if (VanillaHudStyle.bossBars(graphics, events.values()))
            ci.cancel();
    }

    @ModifyVariable(method = "extractRenderState", at = @At("STORE"), ordinal = 0)
    private Component krs$spoofBossBarName(Component name) {
        return ServerAddressSpoof.bossBar(name);
    }
}
