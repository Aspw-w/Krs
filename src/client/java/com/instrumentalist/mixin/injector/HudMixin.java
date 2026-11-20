package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.dev.TextDebugger;
import com.instrumentalist.krs.hacks.features.render.HUDHider;
import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.world.scores.PlayerTeam;
import net.minecraft.world.scores.Scoreboard;
import java.util.Comparator;
import java.util.List;
import com.instrumentalist.krs.hacks.features.render.NoRender;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.DeltaTracker;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.Hud;
import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.ServerAddressSpoof;
import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.contextualbar.ContextualBar;
import net.minecraft.core.component.DataComponents;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.client.gui.Font;
import com.mojang.renderpearl.api.pipeline.RenderPipeline;
import net.minecraft.resources.Identifier;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.PlayerScoreEntry;
import net.minecraft.world.scores.Team;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(Hud.class)
public class HudMixin implements IMinecraft {

    @Inject(method = "extractVignette", at = @At("HEAD"), cancellable = true)
    private void noRenderVignette(GuiGraphicsExtractor graphics, Entity camera, CallbackInfo ci) {
        if (NoRender.active(NoRender.vignette))
            ci.cancel();
    }

    @Inject(method = "extractPortalOverlay", at = @At("HEAD"), cancellable = true)
    private void noRenderPortalOverlay(GuiGraphicsExtractor graphics, float alpha, CallbackInfo ci) {
        if (NoRender.active(NoRender.portalOverlay))
            ci.cancel();
    }

    @Inject(method = "extractConfusionOverlay", at = @At("HEAD"), cancellable = true)
    private void noRenderConfusionOverlay(GuiGraphicsExtractor graphics, float strength, CallbackInfo ci) {
        if (NoRender.active(NoRender.portalOverlay))
            ci.cancel();
    }

    @Inject(method = "extractScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void hideScoreboard(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ModuleManager.getModuleState(HUDHider.class) && HUDHider.board.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractBossOverlay", at = @At("HEAD"), cancellable = true)
    private void hideBossBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ModuleManager.getModuleState(HUDHider.class) && HUDHider.bos.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractOverlayMessage", at = @At("HEAD"), cancellable = true)
    private void hideActionBar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ModuleManager.getModuleState(HUDHider.class) && HUDHider.bar.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "extractTitle", at = @At("HEAD"), cancellable = true)
    private void hideTitle(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        if (ModuleManager.getModuleState(HUDHider.class) && HUDHider.titled.get()) {
            ci.cancel();
        }
    }

    @Shadow
    @Final
    private static Comparator<PlayerScoreEntry> SCORE_DISPLAY_ORDER;

    @Inject(method = "displayScoreboardSidebar", at = @At("HEAD"), cancellable = true)
    private void krs$styledSidebar(GuiGraphicsExtractor graphics, Objective objective, CallbackInfo ci) {
        if (!Interface.styleScoreboard())
            return;

        boolean raw = TextDebugger.scoreboardActive();
        Scoreboard scoreboard = objective.getScoreboard();
        List<PlayerScoreEntry> entries = scoreboard.listPlayerScores(objective).stream()
                .filter(entry -> !entry.isHidden())
                .sorted(SCORE_DISPLAY_ORDER)
                .limit(15L)
                .toList();
        Component[] names = new Component[entries.size()];
        for (int i = 0; i < entries.size(); i++) {
            PlayerScoreEntry entry = entries.get(i);
            PlayerTeam team = scoreboard.getPlayersTeam(entry.owner());
            Component name = ServerAddressSpoof.scoreboard(PlayerTeam.formatNameForTeam(team, entry.ownerName()));
            names[i] = raw ? TextDebugger.raw(name) : name;
        }
        Component displayName = ServerAddressSpoof.scoreboard(objective.getDisplayName());
        Component title = raw ? TextDebugger.raw(displayName) : displayName;
        if (VanillaHudStyle.scoreboard(graphics, title, names))
            ci.cancel();
    }

    @WrapOperation(method = "displayScoreboardSidebar", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/Objective;getDisplayName()Lnet/minecraft/network/chat/Component;"))
    private Component rawSidebarTitle(Objective objective, Operation<Component> original) {
        Component title = ServerAddressSpoof.scoreboard(original.call(objective));
        return TextDebugger.scoreboardActive() ? TextDebugger.raw(title) : title;
    }

    @WrapOperation(method = "lambda$displayScoreboardSidebar$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/PlayerTeam;formatNameForTeam(Lnet/minecraft/world/scores/Team;Lnet/minecraft/network/chat/Component;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent rawSidebarName(Team team, Component name, Operation<MutableComponent> original) {
        Component spoofed = ServerAddressSpoof.scoreboard(original.call(team, name));
        MutableComponent formatted = spoofed instanceof MutableComponent mutable ? mutable : spoofed.copy();
        return TextDebugger.scoreboardActive() ? TextDebugger.raw(formatted) : formatted;
    }

    @WrapOperation(method = "lambda$displayScoreboardSidebar$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/PlayerScoreEntry;formatValue(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent rawSidebarScore(PlayerScoreEntry entry, NumberFormat format, Operation<MutableComponent> original) {
        MutableComponent score = original.call(entry, format);
        return TextDebugger.scoreboardActive() ? TextDebugger.raw(score) : score;
    }

    @Shadow
    @Final
    private static Identifier HOTBAR_SPRITE;

    @Shadow
    @Final
    private static Identifier HOTBAR_SELECTION_SPRITE;

    @Shadow
    @Final
    private static Identifier HOTBAR_OFFHAND_LEFT_SPRITE;

    @Shadow
    @Final
    private static Identifier HOTBAR_OFFHAND_RIGHT_SPRITE;

    @Shadow
    @Final
    private static Identifier HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE;

    @Shadow
    private int toolHighlightTimer;

    @Shadow
    private ItemStack lastToolHighlight;

    @Inject(method = "extractHotbarAndDecorations", at = @At("HEAD"))
    private void krs$beginStyledHotbar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudStyle.beginHotbar(Interface.styleHotbar());
    }

    @Inject(method = "extractHotbarAndDecorations", at = @At("RETURN"))
    private void krs$endStyledHotbar(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudStyle.endHotbar(graphics);
    }

    @WrapOperation(method = "extractHotbarAndDecorations", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/contextualbar/ContextualBar;extractExperienceLevel(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;I)V"))
    private void krs$styledExperienceLevel(GuiGraphicsExtractor graphics, Font font, int level, Operation<Void> original) {
        if (VanillaHudStyle.hotbarActive())
            VanillaHudStyle.hotbarLevel(level);
        else
            original.call(graphics, font, level);
    }

    @Inject(method = "extractSelectedItemName", at = @At("HEAD"), cancellable = true)
    private void krs$styledSelectedItemName(GuiGraphicsExtractor graphics, CallbackInfo ci) {
        if (!VanillaHudStyle.hotbarActive())
            return;
        ci.cancel();
        if (toolHighlightTimer <= 0 || lastToolHighlight.isEmpty())
            return;
        MutableComponent name = Component.empty().append(lastToolHighlight.getHoverName()).withStyle(lastToolHighlight.getRarity().color());
        if (lastToolHighlight.has(DataComponents.CUSTOM_NAME))
            name.withStyle(ChatFormatting.ITALIC);
        int y = graphics.guiHeight() - 59;
        if (!mc.gameMode.canHurtPlayer())
            y += 14;
        VanillaHudStyle.hotbarItemName(name, Math.min(255, toolHighlightTimer * 256f / 10f) / 255f, y);
    }

    @WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIII)V"))
    private void krs$styledHotbarSprite(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int x, int y, int width, int height, Operation<Void> original) {
        if (!VanillaHudStyle.hotbarActive()) {
            original.call(graphics, pipeline, sprite, x, y, width, height);
            return;
        }
        if (sprite.equals(HOTBAR_SPRITE))
            VanillaHudStyle.hotbar(graphics, x, y, width, height);
        else if (sprite.equals(HOTBAR_SELECTION_SPRITE))
            VanillaHudStyle.hotbarSelection(graphics, x, y, width, height);
        else if (sprite.equals(HOTBAR_OFFHAND_LEFT_SPRITE))
            VanillaHudStyle.hotbarOffhand(graphics, x, y, true);
        else if (sprite.equals(HOTBAR_OFFHAND_RIGHT_SPRITE))
            VanillaHudStyle.hotbarOffhand(graphics, x, y, false);
        else if (sprite.equals(HOTBAR_ATTACK_INDICATOR_BACKGROUND_SPRITE))
            VanillaHudStyle.hotbarAttackBackground(graphics, x, y, width, height);
        else
            original.call(graphics, pipeline, sprite, x, y, width, height);
    }

    @WrapOperation(method = "extractItemHotbar", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;blitSprite(Lcom/mojang/renderpearl/api/pipeline/RenderPipeline;Lnet/minecraft/resources/Identifier;IIIIIIII)V"))
    private void krs$styledHotbarAttackProgress(GuiGraphicsExtractor graphics, RenderPipeline pipeline, Identifier sprite, int textureWidth, int textureHeight, int u, int v, int x, int y, int width, int height, Operation<Void> original) {
        if (VanillaHudStyle.hotbarActive())
            VanillaHudStyle.hotbarAttackProgress(height, textureHeight);
        else
            original.call(graphics, pipeline, sprite, textureWidth, textureHeight, u, v, x, y, width, height);
    }

    @WrapOperation(method = "extractTitle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V", ordinal = 0))
    private void krs$styledTitle(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int color, Operation<Void> original) {
        if (Interface.styleTitle())
            VanillaHudStyle.title(graphics, text, false, color);
        else
            original.call(graphics, font, text, x, y, width, color);
    }

    @WrapOperation(method = "extractTitle", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V", ordinal = 1))
    private void krs$styledSubtitle(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int color, Operation<Void> original) {
        if (Interface.styleTitle())
            VanillaHudStyle.title(graphics, text, true, color);
        else
            original.call(graphics, font, text, x, y, width, color);
    }

    @WrapOperation(method = "extractOverlayMessage", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;textWithBackdrop(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;IIII)V"))
    private void krs$styledActionBar(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int width, int color, Operation<Void> original) {
        if (Interface.styleActionBar())
            VanillaHudStyle.actionBar(graphics, text, color);
        else
            original.call(graphics, font, text, x, y, width, color);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;nextStratum()V", ordinal = 0))
    private void krs$markHudLayerSplit(GuiGraphicsExtractor graphics, Operation<Void> original) {
        original.call(graphics);
        VanillaHudStyle.markHudSplit(graphics.guiRenderState);
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Hud;extractHotbarAndDecorations(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/DeltaTracker;)V", shift = At.Shift.AFTER))
    private void krs$markHudTextLayer(GuiGraphicsExtractor graphics, DeltaTracker deltaTracker, CallbackInfo ci) {
        VanillaHudStyle.markHudTextSplit(graphics);
    }
}
