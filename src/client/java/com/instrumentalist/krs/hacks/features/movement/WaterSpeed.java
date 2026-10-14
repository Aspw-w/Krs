package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.input.Keys;

public class WaterSpeed extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Scale", "Strafe", "Boost"}, "Scale");

    @Setting
    private final FloatValue multiplier = new FloatValue("Multiplier", 1.2f, 1.0f, 3.0f);

    @Setting
    private final FloatValue maxSpeed = new FloatValue("Max Speed", 0.5f, 0.1f, 2.0f);

    @Setting
    private final BooleanValue lava = new BooleanValue("Lava", false);

    public WaterSpeed() {
        super("Water Speed", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null || !MovementUtil.isMoving() || !player.isInWater() && (!lava.get() || !player.isInLava()))
            return;

        if (mode.get().equalsIgnoreCase("strafe"))
            MovementUtil.strafe(maxSpeed.get());
        else if (mode.get().equalsIgnoreCase("boost"))
            MovementUtil.boost((multiplier.get() - 1.0f) * 0.08f, maxSpeed.get());
        else
            MovementUtil.scaleVelocityXZ(multiplier.get());
        MovementUtil.limitSpeed(maxSpeed.get());
    }
}
