package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;

public class ClickGui extends Module {

    public ClickGui() {
        super("Click Gui", ModuleCategory.Render, Keys.KEY_RIGHT_SHIFT, true, false);
    }

    public static int getOpenGuiKey() {
        ClickGui clickGui = ModuleManager.getModule(ClickGui.class);
        return clickGui != null ? clickGui.key : Keys.KEY_UNKNOWN;
    }

    @Override
    public String description() {
        return "344 is RIGHT_SHIFT";
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        if (Client.loaded)
            this.toggle();
    }
}
