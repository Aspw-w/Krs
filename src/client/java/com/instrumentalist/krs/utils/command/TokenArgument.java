package com.instrumentalist.krs.utils.command;

import com.mojang.brigadier.StringReader;
import com.mojang.brigadier.arguments.ArgumentType;

import java.util.List;

public final class TokenArgument implements ArgumentType<String> {
    private static final List<String> EXAMPLES = List.of("word", "~", "~-5", "a|b");

    private TokenArgument() {
    }

    public static TokenArgument token() {
        return new TokenArgument();
    }

    @Override
    public String parse(StringReader reader) {
        int start = reader.getCursor();
        while (reader.canRead() && reader.peek() != ' ')
            reader.skip();
        return reader.getString().substring(start, reader.getCursor());
    }

    @Override
    public List<String> getExamples() {
        return EXAMPLES;
    }
}
