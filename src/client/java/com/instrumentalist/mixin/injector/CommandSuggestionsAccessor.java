package com.instrumentalist.mixin.injector;

import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.util.FormattedCharSequence;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.List;

@Mixin(CommandSuggestions.class)
public interface CommandSuggestionsAccessor {
    @Accessor("suggestions")
    CommandSuggestions.SuggestionsList krs$getSuggestions();

    @Accessor("commandUsage")
    List<FormattedCharSequence> krs$getCommandUsage();
}
