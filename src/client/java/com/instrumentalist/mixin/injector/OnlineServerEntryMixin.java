package com.instrumentalist.mixin.injector;

import com.google.common.base.Function;
import com.instrumentalist.krs.hacks.features.dev.TextDebugger;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.screens.multiplayer.ServerSelectionList;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ServerSelectionList.OnlineServerEntry.class)
public class OnlineServerEntryMixin {

    @WrapOperation(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void rawName(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, Operation<Void> original) {
        original.call(graphics, font, TextDebugger.serverListActive() ? TextDebugger.raw(text) : text, x, y, color);
    }

    @WrapOperation(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;split(Lnet/minecraft/network/chat/FormattedText;I)Ljava/util/List;"))
    private List<FormattedCharSequence> rawMotd(Font font, FormattedText text, int width, Operation<List<FormattedCharSequence>> original) {
        return original.call(font, TextDebugger.serverListActive() ? TextDebugger.rawText(text) : text, width);
    }

    @WrapOperation(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/Font;width(Lnet/minecraft/network/chat/FormattedText;)I"))
    private int rawStatusWidth(Font font, FormattedText text, Operation<Integer> original) {
        return original.call(font, TextDebugger.serverListActive() ? TextDebugger.rawText(text) : text);
    }

    @WrapOperation(method = "extractContent", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Lnet/minecraft/network/chat/Component;III)V"))
    private void rawStatus(GuiGraphicsExtractor graphics, Font font, Component text, int x, int y, int color, Operation<Void> original) {
        original.call(graphics, font, TextDebugger.serverListActive() ? TextDebugger.raw(text) : text, x, y, color);
    }

    @WrapOperation(method = "extractContent", at = @At(value = "INVOKE", target = "Lcom/google/common/collect/Lists;transform(Ljava/util/List;Lcom/google/common/base/Function;)Ljava/util/List;"))
    private List<?> rawPlayers(List<?> list, Function<?, ?> function, Operation<List<?>> original) {
        if (!TextDebugger.serverListActive())
            return original.call(list, function);
        return original.call(list.stream().map(line -> line instanceof Component component ? TextDebugger.raw(component) : line).toList(), function);
    }
}
