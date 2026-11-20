package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.IMinecraft;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.ChatCommands;
import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.ActiveTextCollector;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Shadow;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import org.spongepowered.asm.mixin.Unique;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import xyz.breadloaf.imguimc.customwindow.ModuleRenderable;

@Mixin(ChatScreen.class)
public abstract class ChatScreenMixin extends Screen implements IMinecraft {

    protected ChatScreenMixin(Component title) {
        super(title);
    }

    @Inject(method = "mouseClicked", at = @At("HEAD"), cancellable = true)
    private void krs$pressChatScrollbar(MouseButtonEvent event, boolean doubleClick, CallbackInfoReturnable<Boolean> cir) {
        if (event.button() != Keys.MOUSE_BUTTON_LEFT || !Interface.styleChat())
            return;
        if (VanillaHudStyle.suggestionScrollbarPress(krs$suggestionList(), event.x(), event.y())
                || VanillaHudStyle.chatScrollbarPress(event.x(), event.y()))
            cir.setReturnValue(true);
    }

    @Override
    public boolean mouseDragged(MouseButtonEvent event, double dragX, double dragY) {
        if (VanillaHudStyle.suggestionScrollbarDrag(krs$suggestionList(), event.y()) || VanillaHudStyle.chatScrollbarDrag(event.y()))
            return true;
        return super.mouseDragged(event, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(MouseButtonEvent event) {
        boolean suggestionReleased = VanillaHudStyle.suggestionScrollbarRelease();
        if (VanillaHudStyle.chatScrollbarRelease() || suggestionReleased)
            return true;
        return super.mouseReleased(event);
    }

    @Inject(method = "handleChatInput", at = @At("HEAD"), cancellable = true)
    private void chatCommandHook(String message, boolean addToHistory, CallbackInfo ci) {
        if (ModuleManager.getModuleState(ChatCommands.class) && message.startsWith(ChatCommands.prefix.get())) {
            ci.cancel();
            ModuleRenderable.executeCommand(message.substring(ChatCommands.prefix.get().length()), true);
            mc.gui.hud.getChat().addRecentChat(message);
        }
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V", ordinal = 0))
    private void krs$styledChatInput(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (Interface.styleChat())
            VanillaHudStyle.chatInput(graphics, x0, y0, x1, y1);
        else
            original.call(graphics, x0, y0, x1, y1, color);
    }

    @Shadow
    private boolean insertionClickMode() {
        throw new AssertionError();
    }

    @WrapOperation(method = "mouseClicked", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/ActiveTextCollector$ClickableStyleFinder;result()Lnet/minecraft/network/chat/Style;"))
    private Style krs$styledChatClick(ActiveTextCollector.ClickableStyleFinder finder, Operation<Style> original, MouseButtonEvent event, boolean doubleClick) {
        if (Interface.styleChat() && VanillaHudStyle.chatClickHandled(event.x(), event.y()))
            return VanillaHudStyle.chatClickStyle(event.x(), event.y(), insertionClickMode());
        return original.call(finder);
    }

    @Shadow
    protected EditBox input;

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void krs$beginChatScreenLayer(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        VanillaHudStyle.beginChatScreen(graphics, Interface.styleChat(), input);
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void krs$endChatScreenLayer(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        VanillaHudStyle.endChatScreen();
    }

    @Inject(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/components/ChatComponent;extractRenderState(Lnet/minecraft/client/gui/GuiGraphicsExtractor;Lnet/minecraft/client/gui/Font;IIILnet/minecraft/client/gui/components/ChatComponent$DisplayMode;Z)V", shift = At.Shift.AFTER))
    private void krs$beginChatOverlayLayer(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        VanillaHudStyle.beginChatOverlay(graphics);
    }

    @Shadow
    private CommandSuggestions commandSuggestions;

    @Unique
    private Object krs$suggestionList() {
        return commandSuggestions == null ? null : ((CommandSuggestionsAccessor) commandSuggestions).krs$getSuggestions();
    }
}
