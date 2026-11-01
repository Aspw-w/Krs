package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.ChatCommands;
import com.instrumentalist.krs.utils.command.ChatCommandTree;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.suggestion.Suggestions;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.util.FormattedCharSequence;
import org.jspecify.annotations.Nullable;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.List;
import java.util.concurrent.CompletableFuture;

@Mixin(CommandSuggestions.class)
public abstract class CommandSuggestionsMixin {
    @Shadow
    @Final
    private Minecraft minecraft;

    @Shadow
    @Final
    private EditBox input;

    @Shadow
    @Final
    private boolean commandsOnly;

    @Shadow
    @Final
    private List<FormattedCharSequence> commandUsage;

    @Shadow
    private @Nullable ParseResults<ClientSuggestionProvider> currentParse;

    @Shadow
    private @Nullable CompletableFuture<Suggestions> pendingSuggestions;

    @Shadow
    private CommandSuggestions.@Nullable SuggestionsList suggestions;

    @Shadow
    private boolean currentParseIsCommand;

    @Shadow
    private boolean currentParseIsMessage;

    @Shadow
    private boolean keepSuggestions;

    @Shadow
    private void updateUsageInfo(ParseResults<ClientSuggestionProvider> currentParse, Suggestions suggestions) {
    }

    @Inject(method = "updateCommandInfo", at = @At("HEAD"), cancellable = true)
    private void chatCommandSuggestionsHook(CallbackInfo ci) {
        String prefix = ChatCommands.prefix.get();
        String command = input.getValue();
        if (commandsOnly || prefix.isEmpty() || prefix.startsWith("/") || !command.startsWith(prefix)
                || minecraft.player == null || !ModuleManager.getModuleState(ChatCommands.class))
            return;

        ci.cancel();
        if (currentParse != null && !currentParse.getReader().getString().equals(command)) {
            currentParse = null;
            currentParseIsCommand = false;
            currentParseIsMessage = false;
        }

        if (!keepSuggestions) {
            input.setSuggestion(null);
            suggestions = null;
        }

        commandUsage.clear();
        CommandDispatcher<ClientSuggestionProvider> commands = ChatCommandTree.dispatcher();
        if (currentParse == null) {
            StringReader reader = new StringReader(command);
            reader.setCursor(prefix.length());
            currentParse = commands.parse(reader, minecraft.player.connection.getSuggestionsProvider());
            currentParseIsCommand = false;
            currentParseIsMessage = false;
        }

        int cursorPosition = input.getCursorPosition();
        if (cursorPosition < prefix.length()) {
            pendingSuggestions = null;
            return;
        }
        if (suggestions != null && keepSuggestions)
            return;

        ParseResults<ClientSuggestionProvider> parse = currentParse;
        CompletableFuture<Suggestions> pending = commands.getCompletionSuggestions(parse, cursorPosition);
        pendingSuggestions = pending;
        pending.thenAccept(result -> {
            if (pendingSuggestions == pending && pending.isDone())
                updateUsageInfo(parse, result);
        });
    }
}
