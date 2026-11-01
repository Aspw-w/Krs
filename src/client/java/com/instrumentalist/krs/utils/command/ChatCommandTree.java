package com.instrumentalist.krs.utils.command;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.mojang.blaze3d.platform.InputConstants;
import com.mojang.brigadier.Command;
import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.DoubleArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.suggestion.SuggestionProvider;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientSuggestionProvider;
import net.minecraft.commands.SharedSuggestionProvider;
import net.minecraft.world.entity.player.Player;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

public final class ChatCommandTree {
    private static final Command<ClientSuggestionProvider> NOOP = context -> Command.SINGLE_SUCCESS;
    private static final int MAX_SCANCODE = 512;
    private static CommandDispatcher<ClientSuggestionProvider> dispatcher;
    private static List<String> keyNames;

    private static final SuggestionProvider<ClientSuggestionProvider> TOGGLE_MODULES =
            (context, builder) -> SharedSuggestionProvider.suggest(moduleNames(ModuleManager.modules), builder);
    private static final SuggestionProvider<ClientSuggestionProvider> ALL_MODULES =
            (context, builder) -> SharedSuggestionProvider.suggest(moduleNames(ModuleManager.allModules), builder);
    private static final SuggestionProvider<ClientSuggestionProvider> KEYS =
            (context, builder) -> SharedSuggestionProvider.suggest(keyNames(), builder);
    private static final SuggestionProvider<ClientSuggestionProvider> TP_TARGETS =
            (context, builder) -> {
                List<String> targets = new ArrayList<>(playerNames());
                targets.add("~");
                return SharedSuggestionProvider.suggest(targets, builder);
            };
    private static final SuggestionProvider<ClientSuggestionProvider> RELATIVE =
            (context, builder) -> SharedSuggestionProvider.suggest(List.of("~"), builder);

    private ChatCommandTree() {
    }

    public static CommandDispatcher<ClientSuggestionProvider> dispatcher() {
        if (dispatcher == null)
            dispatcher = build();
        return dispatcher;
    }

    private static CommandDispatcher<ClientSuggestionProvider> build() {
        CommandDispatcher<ClientSuggestionProvider> commands = new CommandDispatcher<>();

        for (String name : List.of("help", "commands", "binds", "ign", "name", "crash", "copynbt", "pl", "plugins", "transaction", "session"))
            commands.register(literal(name).executes(NOOP));

        commands.register(literal("say")
                .then(argument("message", StringArgumentType.greedyString()).executes(NOOP)));

        for (String name : List.of("t", "toggle"))
            commands.register(literal(name)
                    .then(argument("module", StringArgumentType.word()).suggests(TOGGLE_MODULES).executes(NOOP)));

        commands.register(literal("bind")
                .then(argument("module", StringArgumentType.word()).suggests(ALL_MODULES)
                        .then(argument("key", StringArgumentType.word()).suggests(KEYS).executes(NOOP))));

        commands.register(literal("tp")
                .then(argument("target", StringArgumentType.word()).suggests(TP_TARGETS).executes(NOOP)
                        .then(argument("y", StringArgumentType.word()).suggests(RELATIVE)
                                .then(argument("z", StringArgumentType.word()).suggests(RELATIVE).executes(NOOP)))));

        commands.register(literal("vclip")
                .then(argument("height", DoubleArgumentType.doubleArg()).executes(NOOP)));

        commands.register(literal("notify")
                .then(argument("title", StringArgumentType.word())
                        .then(argument("message", StringArgumentType.word()).executes(NOOP))));

        commands.register(literal("server").executes(NOOP)
                .then(argument("address", StringArgumentType.greedyString()).executes(NOOP)));

        return commands;
    }

    private static LiteralArgumentBuilder<ClientSuggestionProvider> literal(String name) {
        return LiteralArgumentBuilder.literal(name);
    }

    private static <T> RequiredArgumentBuilder<ClientSuggestionProvider, T> argument(String name, com.mojang.brigadier.arguments.ArgumentType<T> type) {
        return RequiredArgumentBuilder.argument(name, type);
    }

    private static Collection<String> moduleNames(Collection<Module> modules) {
        List<String> names = new ArrayList<>(modules.size());
        for (Module module : modules)
            names.add(module.moduleName.replace(" ", ""));
        return names;
    }

    private static Collection<String> playerNames() {
        Minecraft minecraft = Minecraft.getInstance();
        List<String> names = new ArrayList<>();
        if (minecraft.level == null)
            return names;

        for (Player player : minecraft.level.players()) {
            if (player != minecraft.player)
                names.add(player.getName().getString());
        }
        return names;
    }

    private static List<String> keyNames() {
        if (keyNames != null)
            return keyNames;

        LinkedHashSet<String> names = new LinkedHashSet<>();
        names.add("none");
        String prefix = "key.keyboard.";
        for (int scancode = 1; scancode < MAX_SCANCODE; scancode++) {
            String name = InputConstants.Type.KEYBOARD.getOrCreate(scancode).getName();
            if (!name.startsWith(prefix))
                continue;

            String shortName = name.substring(prefix.length()).toLowerCase(Locale.ROOT);
            if (shortName.length() > 1 && shortName.chars().allMatch(Character::isDigit))
                continue;
            names.add(shortName);
        }

        keyNames = List.copyOf(names);
        return keyNames;
    }
}
