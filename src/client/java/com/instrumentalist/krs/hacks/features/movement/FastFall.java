package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.input.Keys;

public class FastFall extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Shift", "Always"}, "Shift");

    @Setting
    private final FloatValue motion = new FloatValue("Motion", 0.8f, 0.1f, 4.0f);

    public FastFall() {
        super("Fast Fall", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null || player.onGround() || player.isFallFlying() || player.isInWater() || player.isInLava())
            return;

        if (mode.get().equalsIgnoreCase("shift") && !mc.options.keyShift.isDown())
            return;

        if (player.getDeltaMovement().y < 0.0)
            MovementUtil.setVelocityY(-motion.get());
    }
}
