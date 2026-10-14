package com.instrumentalist.krs.hacks.features.movement;



import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.input.Keys;

public class NoJumpCooldown extends Module {

    public NoJumpCooldown() {
        super("No Jump Cooldown", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || !mc.player.onGround()) return;

        mc.player.noJumpDelay = 0;
    }
}
