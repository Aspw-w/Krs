package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.GuiInputBlocker;
import net.minecraft.client.gui.components.AbstractWidget;
import org.spongepowered.asm.mixin.Mixin;
import com.llamalad7.mixinextras.injector.ModifyReturnValue;
import org.spongepowered.asm.mixin.injection.At;

@Mixin(AbstractWidget.class)
public abstract class AbstractWidgetMixin {

    @ModifyReturnValue(method = "isMouseOver(DD)Z", at = @At("RETURN"))
    private boolean blockHoverWhenImguiCaptures(boolean original) {
        return original && !GuiInputBlocker.shouldBlockMinecraftMouse();
    }
}
