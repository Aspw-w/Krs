package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.TextValue;
import com.instrumentalist.krs.utils.input.Keys;

public class ChatCommands extends Module {

    @Setting
    public static final TextValue prefix = new TextValue("Prefix", ".");

    public ChatCommands() {
        super("Chat Commands", ModuleCategory.Player, Keys.KEY_UNKNOWN, true, false);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

}
