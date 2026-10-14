package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.KeyMapping;
import net.minecraft.core.BlockPos;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;

public class AutoSneak extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Always", "Air", "Edge", "Use Item", "Hurt"}, "Always");

    private boolean pressingSneak = false;

    public AutoSneak() {
        super("Auto Sneak", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        releaseSneak();
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) {
            releaseSneak();
            return;
        }

        boolean shouldSneak = switch (mode.get().toLowerCase(Locale.ROOT)) {
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
