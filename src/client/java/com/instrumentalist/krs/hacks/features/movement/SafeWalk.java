package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.BlockEdgeEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;

public class SafeWalk extends Module {
    @Setting
    private final BooleanValue safeWalk = new BooleanValue("Safe Walk", true);

    @Setting
    private final BooleanValue onlyMoving = new BooleanValue("Only Moving", false, safeWalk::get);

    @Setting
    private final BooleanValue ignoreSneak = new BooleanValue("Ignore Sneak", false, safeWalk::get);

    @Setting
    private final BooleanValue parkour = new BooleanValue("Parkour", false);

    @Setting
    private final IntValue parkourJumpDelay = new IntValue("Parkour Jump Delay", 30, 0, 300, "ms", parkour::get);

    @Setting
    private final BooleanValue parkourOnlyForward = new BooleanValue("Parkour Only Forward", false, parkour::get);

    @Setting
    private final BooleanValue autoSneak = new BooleanValue("Auto Sneak", false);

    @Setting
    private final ListValue sneakMode = new ListValue("Sneak Mode", new String[]{"Always", "Air", "Edge", "Use Item", "Hurt"}, "Always", autoSneak::get);

    private final MSTimer jumpTimer = new MSTimer();
    private boolean pressingSneak = false;

    public SafeWalk() {
        super("Safe Walk", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        jumpTimer.reset();
    }

    @Override
    public void onDisable() {
        releaseSneak();
    }

    @Override
    public void onBlockEdge(BlockEdgeEvent event) {
        if (!safeWalk.get())
            return;

        var player = mc.player;
        if (player == null || player.isFallFlying() || player.getAbilities().flying)
            return;

        if (onlyMoving.get() && !MovementUtil.isMoving())
            return;

        if (!ignoreSneak.get() && player.isShiftKeyDown())
            return;

        event.cancel();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (parkour.get())
            updateParkour();

        if (autoSneak.get())
            updateAutoSneak();
        else
            releaseSneak();
    }

    private void updateParkour() {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null || !player.onGround() || player.isShiftKeyDown() || player.getAbilities().flying || player.isInWater() || player.isInLava())
            return;

        if (parkourOnlyForward.get() ? !MovementUtil.isMovingForward() : !MovementUtil.isMoving())
            return;

        if (!level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 1.0, player.getZ())).isAir())
            return;

        if (!jumpTimer.hasTimePassed(parkourJumpDelay.get()))
            return;

        player.input.makeJump();
        jumpTimer.reset();
    }

    private void updateAutoSneak() {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) {
            releaseSneak();
            return;
        }

        boolean shouldSneak = switch (sneakMode.get().toLowerCase(Locale.ROOT)) {
            case "air" -> !player.onGround();
            case "edge" -> player.onGround() && level.getBlockState(BlockPos.containing(player.getX(), player.getY() - 1.0, player.getZ())).isAir();
            case "use item" -> player.isUsingItem();
            case "hurt" -> player.hurtTime > 0;
            default -> true;
        };

        if (shouldSneak) {
            mc.options.keyShift.setDown(true);
            pressingSneak = true;
        } else {
            releaseSneak();
        }
    }

    private void releaseSneak() {
        if (!pressingSneak)
            return;

        KeyMapping.setAll();
        pressingSneak = false;
    }
}
