package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.features.render.Interface;
import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import com.llamalad7.mixinextras.injector.wrapoperation.Operation;
import com.llamalad7.mixinextras.injector.wrapoperation.WrapOperation;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import com.mojang.brigadier.Message;
import com.mojang.brigadier.suggestion.Suggestion;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.renderer.Rect2i;
import net.minecraft.network.chat.ComponentUtils;
import net.minecraft.world.phys.Vec2;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import java.util.ArrayList;
import java.util.List;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(targets = "net.minecraft.client.gui.components.CommandSuggestions$SuggestionsList")
public abstract class SuggestionsListMixin {

    @Shadow
    @Final
    private Rect2i rect;

    @Shadow
    @Final
    private List<Suggestion> suggestionList;

    @Shadow
    private int offset;

    @Shadow
    private int current;

    @Shadow
    private Vec2 lastMouse;

    @Shadow
    @Final
    CommandSuggestions this$0;

    @Shadow
    public abstract void select(int index);

    @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    private void krs$customSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        if (!Interface.styleChat() || !VanillaHudStyle.screenRoutingActive())
            return;
        ci.cancel();

        CommandSuggestionsAccessor outer = (CommandSuggestionsAccessor) this$0;
        int lineLimit = outer.krs$getSuggestionLineLimit();
        int size = suggestionList.size();
        int limit = Math.min(size, lineLimit);
        int maxOffset = Math.max(size - lineLimit, 0);
        float display = VanillaHudStyle.suggestionScroll(this, offset, maxOffset);

        boolean mouseMoved = lastMouse.x != mouseX || lastMouse.y != mouseY;
        if (mouseMoved)
            lastMouse = new Vec2(mouseX, mouseY);

        boolean hovered = false;
        if (!VanillaHudStyle.suggestionScrollbarContains(mouseX, mouseY)
                && mouseX > rect.getX() && mouseX < rect.getX() + rect.getWidth()
                && mouseY > rect.getY() && mouseY < rect.getY() + rect.getHeight()) {
            int line = (int) Math.floor((mouseY - rect.getY()) / 12.0 + display);
            if (line >= 0 && line < size) {
                if (mouseMoved)
                    select(line);
                hovered = true;
            }
        }

        int base = Math.clamp((int) Math.floor(display), 0, Math.max(0, size - 1));
        int count = Math.min(limit + 1, size - base);
        List<String> rows = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            rows.add(suggestionList.get(base + i).getText());
        VanillaHudStyle.drawSuggestionList(graphics, rect, rows, base, display, current, size, limit, mouseX, mouseY);

        if (hovered) {
            Message tooltip = suggestionList.get(current).getTooltip();
            if (tooltip != null)
                graphics.setTooltipForNextFrame(outer.krs$getFont(), ComponentUtils.fromMessage(tooltip), mouseX, mouseY);
        }
        if (rect.contains(mouseX, mouseY))
            graphics.requestCursor(CursorTypes.POINTING_HAND);
    }

    @Inject(method = "extractRenderState", at = @At("HEAD"))
    private void krs$beginStyledSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        VanillaHudStyle.beginSuggestions(Interface.styleChat());
    }

    @Inject(method = "extractRenderState", at = @At("RETURN"))
    private void krs$endStyledSuggestions(GuiGraphicsExtractor graphics, int mouseX, int mouseY, CallbackInfo ci) {
        VanillaHudStyle.endSuggestions();
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;fill(IIIII)V"))
    private void krs$styledSuggestionFill(GuiGraphicsExtractor graphics, int x0, int y0, int x1, int y1, int color, Operation<Void> original) {
        if (VanillaHudStyle.suggestionsActive())
            VanillaHudStyle.suggestionFill(graphics, x0, y0, x1, y1, color);
        else
            original.call(graphics, x0, y0, x1, y1, color);
    }

    @WrapOperation(method = "extractRenderState", at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/GuiGraphicsExtractor;text(Lnet/minecraft/client/gui/Font;Ljava/lang/String;III)V"))
    private void krs$styledSuggestionText(GuiGraphicsExtractor graphics, Font font, String text, int x, int y, int color, Operation<Void> original) {
        if (VanillaHudStyle.suggestionsActive())
            VanillaHudStyle.suggestionText(graphics, text, x, y, color);
        else
            original.call(graphics, font, text, x, y, color);
    }
}
