package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.math.RandomUtil;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.KeyMapping;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Locale;

public class AutoWalk extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Forward", "Backward", "Left", "Right", "Jump", "Random"}, "Forward");

    @Setting
    private final BooleanValue onlyInGame = new BooleanValue("Only In Game", true);

    @Setting
    private final BooleanValue sprint = new BooleanValue("Sprint", false);

    @Setting
    private final IntValue switchTicks = new IntValue("Switch Ticks", 20, 5, 100, "t", () -> mode.get().equalsIgnoreCase("random"));

    private boolean pressingMovement = false;
    private int ticks = 0;
    private int randomDirection = 0;

    public AutoWalk() {
        super("Auto Walk", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
        releaseMovement();
        ticks = 0;
        randomDirection = 0;
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null || onlyInGame.get() && mc.gui.screen() != null) {
            releaseMovement();
            return;
        }

        releaseMovement();
        String currentMode = mode.get().toLowerCase(Locale.ROOT);
        if (currentMode.equals("random")) {
            if (ticks <= 0) {
                randomDirection = RandomUtil.nextInt(0, 5);
                ticks = switchTicks.get();
            } else ticks--;
        }

        switch (currentMode.equals("random") ? randomMode() : currentMode) {
            case "backward" -> mc.options.keyDown.setDown(true);
            case "left" -> mc.options.keyLeft.setDown(true);
            case "right" -> mc.options.keyRight.setDown(true);
            case "jump" -> {
                mc.options.keyUp.setDown(true);
                mc.options.keyJump.setDown(true);
            }
            default -> mc.options.keyUp.setDown(true);
        }
        if (sprint.get())
            mc.options.keySprint.setDown(true);
        pressingMovement = true;
    }

    private String randomMode() {
        return switch (randomDirection) {
            case 1 -> "backward";
            case 2 -> "left";
            case 3 -> "right";
            case 4 -> "jump";
            default -> "forward";
        };
    }

    private void releaseMovement() {
        if (!pressingMovement)
            return;

        KeyMapping.setAll();
        pressingMovement = false;
    }
}
