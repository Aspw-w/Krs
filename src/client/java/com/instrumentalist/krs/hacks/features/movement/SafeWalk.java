package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.BlockEdgeEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.input.Keys;

public class SafeWalk extends Module {
    @Setting
    private final BooleanValue onlyMoving = new BooleanValue("Only Moving", false);

    @Setting
    private final BooleanValue ignoreSneak = new BooleanValue("Ignore Sneak", false);

    public SafeWalk() {
        super("Safe Walk", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onBlockEdge(BlockEdgeEvent event) {
        var player = mc.player;
        if (player == null || player.isFallFlying() || player.getAbilities().flying)
            return;

        if (onlyMoving.get() && !MovementUtil.isMoving())
            return;

        if (!ignoreSneak.get() && player.isShiftKeyDown())
            return;

        event.cancel();
    }
}
