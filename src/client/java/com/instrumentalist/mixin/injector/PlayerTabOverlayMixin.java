package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.dev.TextDebugger;
import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.ServerAddressSpoof;
import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.numbers.StyledFormat;
import net.minecraft.world.scores.ScoreHolder;
import net.minecraft.world.scores.criteria.ObjectiveCriteria;
import org.spongepowered.asm.mixin.Shadow;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.Objective;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import net.minecraft.world.scores.Scoreboard;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin implements IMinecraft {

    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component rawName(Component name, PlayerInfo info) {
        Component spoofed = ServerAddressSpoof.tabList(name);
        return TextDebugger.tabListActive() ? TextDebugger.raw(spoofed) : spoofed;
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"))
    private List<FormattedCharSequence> rawHeaderFooter(Font font, FormattedText text, int width, Operation<List<FormattedCharSequence>> original) {
        FormattedText spoofed = text instanceof Component component ? ServerAddressSpoof.tabList(component) : text;
        return original.call(font, TextDebugger.tabListActive() ? TextDebugger.rawText(spoofed) : spoofed, width);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/ReadOnlyScoreInfo;formatValue(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent rawScore(ReadOnlyScoreInfo info, NumberFormat format, Operation<MutableComponent> original) {
        MutableComponent score = original.call(info, format);
        return TextDebugger.tabListActive() ? TextDebugger.raw(score) : score;
    }

    @Shadow
    private List<PlayerInfo> getPlayerInfos() {
        throw new AssertionError();
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void krs$styledTabList(GuiGraphicsExtractor graphics, int screenWidth, Scoreboard scoreboard, @Nullable Objective displayObjective, CallbackInfo ci) {
        if (!Interface.styleTabList() || mc.getConnection() == null)
            return;

        PlayerTabOverlay self = (PlayerTabOverlay) (Object) this;
        List<PlayerInfo> players = getPlayerInfos();
        Component[] names = new Component[players.size()];
        Component[] scores = displayObjective != null ? new Component[players.size()] : null;
        for (int i = 0; i < players.size(); i++) {
            PlayerInfo info = players.get(i);
            names[i] = self.getNameForDisplay(info);
            if (scores == null)
                continue;
            ReadOnlyScoreInfo scoreInfo = scoreboard.getPlayerScoreInfo(ScoreHolder.fromGameProfile(info.getProfile()), displayObjective);
            if (scoreInfo == null)
                continue;
            MutableComponent score = displayObjective.getRenderType() == ObjectiveCriteria.RenderType.HEARTS
                    ? Component.literal(scoreInfo.value() + " ❤").withStyle(ChatFormatting.RED)
                    : scoreInfo.formatValue(displayObjective.numberFormatOrDefault(StyledFormat.PLAYER_LIST_DEFAULT));
            scores[i] = TextDebugger.tabListActive() ? TextDebugger.raw(score) : score;
        }

        Component header = ServerAddressSpoof.tabList(self.header);
        Component footer = ServerAddressSpoof.tabList(self.footer);
        if (TextDebugger.tabListActive()) {
            header = header != null ? TextDebugger.raw(header) : null;
            footer = footer != null ? TextDebugger.raw(footer) : null;
        }

        if (VanillaHudStyle.tabList(graphics, players, names, scores, header, footer, mc.getConnection().onlineMode()))
            ci.cancel();
    }
}
