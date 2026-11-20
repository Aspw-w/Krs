package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.dev.TextDebugger;
import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.utils.ServerAddressSpoof;
import net.minecraft.network.chat.Component;
import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.client.multiplayer.chat.GuiMessage;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.util.Mth;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;

@Mixin(ChatComponent.class)
public class ChatComponentMixin implements IMinecraft {

    @WrapOperation(method = "addMessageToDisplayQueue", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/multiplayer/chat/GuiMessage;splitLines(Lnet/minecraft/client/gui/Font;I)Ljava/util/List;"))
    private List<FormattedCharSequence> rawChat(GuiMessage message, Font font, int maxWidth, Operation<List<FormattedCharSequence>> original) {
        Component content = ServerAddressSpoof.chat(message.content());
        if (TextDebugger.chatActive())
            content = TextDebugger.raw(content);
        if (content == message.content())
            return original.call(message, font, maxWidth);
        GuiMessage replaced = new GuiMessage(message.addedTime(), content, message.signature(), message.source(), message.tag());
        return original.call(replaced, font, maxWidth);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("HEAD"))
    private void krs$beginStyledChat(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        VanillaHudStyle.beginChat(graphics, Interface.styleChat(), displayMode.foreground, mouseX, mouseY, changeCursorOnInsertions);
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", at = @At("RETURN"))
    private void krs$endStyledChat(GuiGraphicsExtractor graphics, Font font, int ticks, int mouseX, int mouseY, ChatComponent.DisplayMode displayMode, boolean changeCursorOnInsertions, CallbackInfo ci) {
        VanillaHudStyle.endChat();
    }

    @WrapOperation(method = "lambda$extractRenderState$1", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V"))
    private static void krs$styledChatLine(ChatComponent.ChatGraphicsAccess graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (!VanillaHudStyle.chatActive()) {
            original.call(graphics, x0, y0, x1, y1, color);
            return;
        }
        float backgroundOpacity = mc.options.textBackgroundOpacity().get().floatValue();
        if (backgroundOpacity <= 0.001f)
            return;
        float lineAlpha = (color >>> 24) / 255f / backgroundOpacity;
        VanillaHudStyle.chatLine(x0, y0, x1, y1, lineAlpha * Math.min(1f, backgroundOpacity * 2f));
    }

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V", at = {
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V", ordinal = 0),
            @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V", ordinal = 1)
    })
    private void krs$styledChatExtraLine(ChatComponent.ChatGraphicsAccess graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (!VanillaHudStyle.chatActive()) {
            original.call(graphics, x0, y0, x1, y1, color);
            return;
        }
        float backgroundOpacity = mc.options.textBackgroundOpacity().get().floatValue();
        if (backgroundOpacity > 0.001f)
            VanillaHudStyle.chatLine(x0, y0, x1, y1, Math.min(1f, backgroundOpacity * 2f));
    }

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V", ordinal = 2))
    private void krs$styledChatScrollbar(ChatComponent.ChatGraphicsAccess graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (!VanillaHudStyle.chatActive())
            original.call(graphics, x0, y0, x1, y1, color);
    }

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;fill(IIIII)V", ordinal = 3))
    private void krs$styledChatScrollbarEdge(ChatComponent.ChatGraphicsAccess graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (!VanillaHudStyle.chatActive())
            original.call(graphics, x0, y0, x1, y1, color);
    }

    @WrapOperation(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;handleMessage(IFLnet/minecraft/util/FormattedCharSequence;)Z"))
    private boolean krs$styledChatExtraMessage(ChatComponent.ChatGraphicsAccess graphics, int textTop, float opacity, FormattedCharSequence message, Operation<Boolean> original) {
        if (!VanillaHudStyle.chatActive())
            return original.call(graphics, textTop, opacity, message);
        Boolean hovered = VanillaHudStyle.chatMessage(textTop, opacity, message);
        return hovered != null ? hovered : original.call(graphics, textTop, opacity, message);
    }

    @Shadow
    private int chatScrollbarPos;

    @Shadow
    @Final
    private List<GuiMessage.Line> trimmedMessages;

    @Shadow
    public int getLinesPerPage() {
        throw new AssertionError();
    }

    @Shadow
    private int getWidth() {
        throw new AssertionError();
    }

    @Shadow
    private double getScale() {
        throw new AssertionError();
    }

    @Unique
    private int krs$realScrollPos = -1;

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V", at = @At("HEAD"))
    private void krs$beginSmoothScroll(ChatComponent.ChatGraphicsAccess graphics, int screenHeight, int ticks, ChatComponent.DisplayMode displayMode, CallbackInfo ci) {
        krs$realScrollPos = -1;
        if (!VanillaHudStyle.chatActive())
            return;
        float scale = (float) getScale();
        int maxWidth = Mth.ceil(getWidth() / scale);
        int chatBottom = Mth.floor((screenHeight - 40) / scale);
        int perPage = getLinesPerPage();
        int lineHeight = (int) (9.0D * (mc.options.chatLineSpacing().get() + 1.0D));
        float bottom = chatBottom * scale;
        float top = (chatBottom - perPage * lineHeight) * scale;
        float right = (maxWidth + 4 + 4 + 4) * scale;
        int base = VanillaHudStyle.chatScroll((ChatComponent) (Object) this, trimmedMessages, chatScrollbarPos, perPage,
                0f, top, right, bottom, lineHeight * scale);
        if (base != chatScrollbarPos) {
            krs$realScrollPos = chatScrollbarPos;
            chatScrollbarPos = base;
        }
    }

    @Inject(method = "extractRenderState(Lnet/minecraft/client/gui/components/ChatComponent$ChatGraphicsAccess;IILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;)V", at = @At("RETURN"))
    private void krs$endSmoothScroll(ChatComponent.ChatGraphicsAccess graphics, int screenHeight, int ticks, ChatComponent.DisplayMode displayMode, CallbackInfo ci) {
        if (krs$realScrollPos >= 0)
            chatScrollbarPos = krs$realScrollPos;
        krs$realScrollPos = -1;
    }

    @WrapOperation(method = "forEachLine", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;getLinesPerPage()I"))
    private int krs$smoothScrollExtraLine(ChatComponent chat, Operation<Integer> original) {
        int lines = original.call(chat);
        return VanillaHudStyle.chatExtraLine() ? lines + 1 : lines;
    }
}
