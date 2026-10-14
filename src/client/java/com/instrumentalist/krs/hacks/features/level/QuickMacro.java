package com.instrumentalist.krs.hacks.features.level;



import com.instrumentalist.krs.events.features.KeyboardEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.KeyBindValue;
import com.instrumentalist.krs.utils.value.TextValue;
import com.instrumentalist.krs.utils.input.Keys;

public class QuickMacro extends Module {

    public QuickMacro() {
        super("Quick Macro", ModuleCategory.Level, Keys.KEY_UNKNOWN, false, true);
    }

    @Setting
    private final KeyBindValue macroKey = new KeyBindValue("Macro Key", Keys.KEY_X);

    @Setting
    private final TextValue messageOrCommand = new TextValue("Message / Command", "");

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onKey(KeyboardEvent event) {
        if (mc.player == null) return;

        if (event.action == Keys.PRESS && event.key == macroKey.get()) {
            boolean commandMode = messageOrCommand.get().startsWith("/");
            if (commandMode) {
                mc.player.connection.sendCommand(messageOrCommand.get().substring(1));
            } else {
                mc.player.connection.sendChat(messageOrCommand.get());
            }
        }
    }
}
