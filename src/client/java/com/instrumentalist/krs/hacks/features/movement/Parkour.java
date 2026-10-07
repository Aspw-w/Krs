package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.core.BlockPos;
import org.lwjgl.glfw.GLFW;

public class Parkour extends Module {
    @Setting
    private final IntValue jumpDelay = new IntValue("Jump Delay", 30, 0, 300, "ms");

    @Setting
    private final BooleanValue onlyForward = new BooleanValue("Only Forward", false);

    private final MSTimer jumpTimer = new MSTimer();

    public Parkour() {
        super("Parkour", ModuleCategory.Movement, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        jumpTimer.reset();
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null || !player.onGround() || player.isShiftKeyDown() || player.getAbilities().flying || player.isInWater() || player.isInLava())
            return;

        if (onlyForward.get() ? !MovementUtil.isMovingForward() : !MovementUtil.isMoving())
            return;

        if (!level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 1.0, player.getZ())).isAir())
            return;

        if (!jumpTimer.hasTimePassed(jumpDelay.get()))
            return;

        player.input.makeJump();
        jumpTimer.reset();
    }
}
