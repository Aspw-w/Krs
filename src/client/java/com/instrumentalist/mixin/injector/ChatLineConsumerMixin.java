package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessageTag;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(targets = "net.minecraft.client.gui.components.ChatComponent$1")
public class ChatLineConsumerMixin {

    @WrapOperation(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
    private boolean krs$styledChatMessage(ChatComponent.ChatGraphicsAccess graphics, int textTop, float opacity, FormattedCharSequence message, Operation<Boolean> original) {
        if (!VanillaHudStyle.chatActive())
            return original.call(graphics, textTop, opacity, message);
        Boolean hovered = VanillaHudStyle.chatMessage(textTop, opacity, message);
        return hovered != null ? hovered : original.call(graphics, textTop, opacity, message);
    }

    @WrapOperation(method = "accept", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleTag(IIIIFLnet/minecraft/client/multiplayer/chat/GuiMessageTag;)V"))
    private void krs$styledChatTag(ChatComponent.ChatGraphicsAccess graphics, int x0, int y0, int x1, int y1, float opacity, GuiMessageTag tag, Operation<Void> original) {
        if (VanillaHudStyle.chatActive())
            VanillaHudStyle.chatTag(x0, y0, x1, y1, opacity, tag);
        else
            original.call(graphics, x0, y0, x1, y1, opacity, tag);
    }
}
