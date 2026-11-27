package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.ListValue;

public class ClickGui extends Module {

    public ClickGui() {
        super("Click Gui", ModuleCategory.Render, Keys.KEY_RIGHT_SHIFT, false, false);
    }

    @Setting
    public static final ListValue mode = new ListValue(
            "Mode",
            new String[]{"Modern", "ImGui"},
            "Modern"
    );

    @Setting
    public static final ListValue theme = new ListValue(
            "Theme",
            new String[]{"Light", "Classic", "Dark"},
            "Classic",
            () -> mode.get().equalsIgnoreCase("imgui")
    );

    public static int getOpenGuiKey() {
        ClickGui clickGui = ModuleManager.getModule(ClickGui.class);
        return clickGui != null ? clickGui.key : Keys.KEY_UNKNOWN;
    }

    public static boolean isImGuiMode() {
        return mode.get().equalsIgnoreCase("imgui");
    }

    @Override
    public String description() {
        return "RIGHT_SHIFT = Code 344 (Default)";
    }

    @Override
    public boolean isBindable() {
        return true;
    }

    @Override
    public boolean isLocked() {
        return true;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }
}
