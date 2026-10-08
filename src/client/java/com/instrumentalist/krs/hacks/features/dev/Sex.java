package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.ChatUtil;
import org.lwjgl.glfw.GLFW;

public class Sex extends Module {

    public Sex() {
        super("Sex", ModuleCategory.Dev, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
        if (Client.loaded) {
            ChatUtil.printChat("Do not use this module");
            this.toggle();
        }
    }
}
