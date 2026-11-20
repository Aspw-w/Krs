package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.render.VanillaHudStyle;
import com.mojang.blaze3d.platform.cursor.CursorTypes;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.IMEPreeditOverlay;
import net.minecraft.client.gui.components.TextCursorUtils;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import net.minecraft.util.Util;
import org.jetbrains.annotations.Nullable;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(EditBox.class)
public abstract class EditBoxMixin extends AbstractWidget {

    @Shadow
    private String value;

    @Shadow
    private int displayPos;

    @Shadow
    private int cursorPos;

    @Shadow
    private int highlightPos;

    @Shadow
    private int textX;

    @Shadow
    private int textY;

    @Shadow
    private long focusedTime;

    @Shadow
    private boolean isEditable;

    @Shadow
    private int textColor;

    @Shadow
    private int textColorUneditable;

    @Shadow
    private @Nullable String suggestion;

    @Shadow
    private @Nullable Component hint;

    @Shadow
    private @Nullable IMEPreeditOverlay preeditOverlay;

    @Shadow
    public abstract boolean isVisible();

    @Shadow
    public abstract int getInnerWidth();

    @Shadow
    public abstract boolean canConsumeInput();

    @Shadow
    private int getMaxLength() {
        throw new AssertionError();
    }

    @Shadow
    private FormattedCharSequence applyFormat(String text, int offset) {
        throw new AssertionError();
    }

    protected EditBoxMixin(int x, int y, int width, int height, Component message) {
        super(x, y, width, height, message);
    }

    @Inject(method = "extractWidgetRenderState", at = @At("HEAD"), cancellable = true)
    private void krs$styledChatInput(GuiGraphicsExtractor graphics, int mouseX, int mouseY, float a, CallbackInfo ci) {
        if (!isVisible() || !VanillaHudStyle.isStyledChatInput(this))
            return;
        ci.cancel();

        int color = isEditable ? textColor : textColorUneditable;
        String tail = value.substring(Math.min(displayPos, value.length()));
        String displayed = VanillaHudStyle.chatInputVisibleText(tail, displayPos, getInnerWidth(), this::applyFormat);
        int relCursorPos = cursorPos - displayPos;
        boolean cursorOnScreen = relCursorPos >= 0 && relCursorPos <= displayed.length();
        boolean showCursor = isFocused() && TextCursorUtils.isCursorVisible(Util.getMillis() - focusedTime) && cursorOnScreen;
        int relHighlightPos = Mth.clamp(highlightPos - displayPos, 0, displayed.length());
        boolean insert = cursorPos < value.length() || value.length() >= getMaxLength();

        float cursorX = VanillaHudStyle.chatInputText(graphics, displayed, displayPos, relCursorPos, relHighlightPos,
                cursorOnScreen, showCursor, insert, suggestion, displayed.isEmpty() && !isFocused() ? hint : null,
                textX, textY, getWidth(), color, this::applyFormat);

        if (isHovered())
            graphics.requestCursor(isEditable ? CursorTypes.IBEAM : CursorTypes.NOT_ALLOWED);
        int cursorGuiX = Math.round(cursorX);
        if (preeditOverlay == null) {
            if (canConsumeInput())
                Minecraft.getInstance().textInputManager().setTextInputArea(cursorGuiX, textY, cursorGuiX + 1, textY + 10);
        } else {
            preeditOverlay.updateInputPosition(cursorGuiX, textY);
            graphics.setPreeditOverlay(preeditOverlay);
        }
    }

    @Inject(method = "findClickedPositionInText", at = @At("HEAD"), cancellable = true)
    private void krs$styledChatInputClick(MouseButtonEvent event, CallbackInfoReturnable<Integer> cir) {
        if (!VanillaHudStyle.isStyledChatInput(this))
            return;
        String tail = value.substring(Math.min(displayPos, value.length()));
        cir.setReturnValue(displayPos + VanillaHudStyle.chatInputClickIndex(tail, displayPos,
                (float) event.x() - textX, getInnerWidth(), this::applyFormat));
    }

    @Inject(method = "getScreenX", at = @At("HEAD"), cancellable = true)
    private void krs$styledChatInputScreenX(int charIndex, CallbackInfoReturnable<Integer> cir) {
        if (VanillaHudStyle.isStyledChatInput(this))
            cir.setReturnValue(VanillaHudStyle.chatInputScreenX(value, charIndex, getX(), this::applyFormat));
    }
}
