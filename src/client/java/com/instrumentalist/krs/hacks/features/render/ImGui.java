package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.input.Keys;

public class ImGui extends Module {

    public ImGui() {
        super("Im Gui", ModuleCategory.Render, Keys.KEY_UNKNOWN, true, false);
    }

    public static int getOpenGuiKey() {
        ImGui imGui = ModuleManager.getModule(ImGui.class);
        return imGui != null ? imGui.key : Keys.KEY_UNKNOWN;
    }

    @Setting
    public static final ListValue theme = new ListValue(
            "Theme",
            new String[]{"Light", "Classic", "Dark"},
            "Classic"
    );

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
