package com.instrumentalist.mixin.injector;

import net.minecraft.client.gui.GuiGraphicsExtractor;
import net.minecraft.network.chat.Style;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(GuiGraphicsExtractor.class)
public interface GuiGraphicsExtractorAccessor {
    @Accessor("hoveredTextStyle")
    void krs$setHoveredTextStyle(Style style);

    @Accessor("clickableTextStyle")
    void krs$setClickableTextStyle(Style style);
}
