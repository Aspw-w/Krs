package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.ChatUtil;
import net.minecraft.world.entity.Entity;
import com.instrumentalist.krs.utils.input.Keys;

public class Debugga extends Module {

    public Debugga() {
        super("Debugga", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onAttack(AttackEvent event) {
        Entity target = event.entity;
        if (target == null) return;
        ChatUtil.printChat(target.getUUID() + "");
    }
}
