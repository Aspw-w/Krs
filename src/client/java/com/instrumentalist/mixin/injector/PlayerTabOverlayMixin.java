package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.dev.TextDebugger;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.PlayerTabOverlay;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.numbers.NumberFormat;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.world.scores.ReadOnlyScoreInfo;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(PlayerTabOverlay.class)
public class PlayerTabOverlayMixin {

    @ModifyReturnValue(method = "getNameForDisplay", at = @At("RETURN"))
    private Component rawName(Component name, PlayerInfo info) {
        return TextDebugger.tabListActive() ? TextDebugger.raw(name) : name;
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"))
    private List<FormattedCharSequence> rawHeaderFooter(Font font, FormattedText text, int width, Operation<List<FormattedCharSequence>> original) {
        return original.call(font, TextDebugger.tabListActive() ? TextDebugger.rawText(text) : text, width);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/world/scores/ReadOnlyScoreInfo;formatValue(Lnet/minecraft/network/chat/numbers/NumberFormat;)Lnet/minecraft/network/chat/MutableComponent;"))
    private MutableComponent rawScore(ReadOnlyScoreInfo info, NumberFormat format, Operation<MutableComponent> original) {
        MutableComponent score = original.call(info, format);
        return TextDebugger.tabListActive() ? TextDebugger.raw(score) : score;
    }
}
