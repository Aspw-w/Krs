package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.input.Keys;

public class Sex extends Module {

    public Sex() {
        super("Sex", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
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
