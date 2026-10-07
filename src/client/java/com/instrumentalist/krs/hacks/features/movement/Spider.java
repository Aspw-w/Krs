package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class Spider extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Vanilla", "Timer", "Pulse"}, "Vanilla");

    @Setting
    private final FloatValue speed = new FloatValue("Speed", 0.32f, 0.1f, 1f, () -> !mode.get().equalsIgnoreCase("timer"));

    @Setting
    private final FloatValue timerSpeed = new FloatValue("Timer Speed", 1.7f, 1.1f, 4f, "x", () -> mode.get().equalsIgnoreCase("timer"));

    @Setting
    private final BooleanValue onlyMoving = new BooleanValue("Only Moving", true);

    private boolean timered = false;
    private int pulseTicks = 0;

    public Spider() {
        super("Spider", ModuleCategory.Movement, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        resetTimer();
        pulseTicks = 0;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null) {
            resetTimer();
            pulseTicks = 0;
            return;
        }

        boolean climbing = player.horizontalCollision && !player.onClimbable() && (!onlyMoving.get() || MovementUtil.isMoving());
        if (!climbing) {
            resetTimer();
            pulseTicks = 0;
            return;
        }

        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "timer" -> {
                TimerUtil.timerSpeed = timerSpeed.get();
                timered = true;
                if (player.getDeltaMovement().y < 0.0)
                    MovementUtil.setVelocityY(0.0);
            }
            case "pulse" -> {
                pulseTicks++;
                if (pulseTicks >= 3) {
                    MovementUtil.setVelocityY(speed.get().doubleValue());
                    pulseTicks = 0;
                } else if (player.getDeltaMovement().y < 0.0) {
                    MovementUtil.setVelocityY(0.0);
                }
            }
            default -> MovementUtil.setVelocityY(speed.get().doubleValue());
        }
    }

    private void resetTimer() {
        if (timered) {
            TimerUtil.reset();
            timered = false;
        }
    }
}
