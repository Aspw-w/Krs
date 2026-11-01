package com.instrumentalist.mixin.injector;

import net.minecraft.client.renderer.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.components.CommandSuggestions$SuggestionsList")
public interface SuggestionsListAccessor {
    @Accessor("rect")
    Rect2i krs$getRect();
}
