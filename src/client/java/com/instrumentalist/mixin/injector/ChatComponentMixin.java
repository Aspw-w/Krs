package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.dev.TextDebugger;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

import java.util.List;

@Mixin(ChatComponent.class)
public class ChatComponentMixin {

    @WrapOperation(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
    private List<FormattedCharSequence> rawChat(GuiMessage message, Font font, int maxWidth, Operation<List<FormattedCharSequence>> original) {
        if (!TextDebugger.chatActive())
            return original.call(message, font, maxWidth);
        GuiMessage raw = new GuiMessage(message.addedTime(), TextDebugger.raw(message.content()), message.signature(), message.source(), message.tag());
        return original.call(raw, font, maxWidth);
    }
}
