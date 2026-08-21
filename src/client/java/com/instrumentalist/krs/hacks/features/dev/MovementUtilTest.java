package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class MovementUtilTest extends Module {
    @Setting
    private final ListValue mode = new ListValue(
            "Mode",
            new String[]{"Info", "Strafe", "Smooth Strafe", "Accelerate", "Boost", "Limit", "Redirect", "Decelerate", "Friction", "Approach", "Vertical", "Jump", "Stop XZ", "Predict"},
            "Info"
    );

    @Setting
    private final FloatValue speed = new FloatValue("Speed", 0.35f, 0.0f, 3.0f, () ->
            mode.get().equalsIgnoreCase("strafe")
                    || mode.get().equalsIgnoreCase("smooth strafe")
                    || mode.get().equalsIgnoreCase("accelerate")
                    || mode.get().equalsIgnoreCase("boost")
                    || mode.get().equalsIgnoreCase("decelerate")
                    || mode.get().equalsIgnoreCase("approach")
                    || mode.get().equalsIgnoreCase("jump")
    );

    @Setting
    private final FloatValue maxSpeed = new FloatValue("Max Speed", 0.75f, 0.0f, 5.0f, () ->
            mode.get().equalsIgnoreCase("accelerate")
                    || mode.get().equalsIgnoreCase("boost")
                    || mode.get().equalsIgnoreCase("limit")
                    || mode.get().equalsIgnoreCase("approach")
    );

    @Setting
    private final FloatValue verticalSpeed = new FloatValue("Vertical Speed", 0.42f, 0.0f, 2.0f, () ->
            mode.get().equalsIgnoreCase("vertical")
                    || mode.get().equalsIgnoreCase("jump")
    );

    @Setting
    private final FloatValue friction = new FloatValue("Friction", 0.08f, 0.0f, 1.0f, () ->
            mode.get().equalsIgnoreCase("friction")
    );

    @Setting
    private final BooleanValue logState = new BooleanValue("Log State", true);

    @Setting
    private final IntValue logInterval = new IntValue("Log Interval", 20, 1, 200, "t", logState::get);

    @Setting
    private final IntValue predictTicks = new IntValue("Predict Ticks", 5, 0, 40, "t", logState::get);

    private int ticks;

    public MovementUtilTest() {
        super("Movement Util Test", ModuleCategory.Dev, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String description() {
        return "Manual tester for MovementUtil helpers";
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onEnable() {
        ticks = 0;
        log("Enabled: " + mode.get());
    }

    @Override
    public void onDisable() {
        log("Disabled");
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        ticks++;
        switch (mode.get().toLowerCase(Locale.ROOT)) {
            case "strafe" -> MovementUtil.strafe(speed.get());
            case "smooth strafe" -> MovementUtil.smoothStrafe(speed.get());
            case "accelerate" -> MovementUtil.accelerate(speed.get(), maxSpeed.get());
            case "boost" -> MovementUtil.boost(speed.get(), maxSpeed.get());
            case "limit" -> MovementUtil.limitSpeed(maxSpeed.get());
            case "redirect" -> MovementUtil.redirectSpeed(MovementUtil.getPlayerDirection());
            case "decelerate" -> MovementUtil.decelerate(speed.get());
            case "friction" -> MovementUtil.applyFriction(friction.get());
            case "approach" -> MovementUtil.approachSpeed(maxSpeed.get(), speed.get());
            case "vertical" -> MovementUtil.setVelocityY(MovementUtil.getVerticalInputMotion(verticalSpeed.get()));
            case "jump" -> {
                if (MovementUtil.isMoving()) MovementUtil.strafe(speed.get());
                MovementUtil.tryJump(verticalSpeed.get());
            }
            case "stop xz" -> MovementUtil.stopXZ();
            default -> {
            }
        }

        if (logState.get() && ticks % Math.max(1, logInterval.get()) == 0)
            log(formatState());
    }

    private void log(String message) {
        if (mc.player != null)
            ChatUtil.printChat("MovementUtil Test: " + message);
    }

    private String formatState() {
        Vec3 predicted = MovementUtil.getPredictedPosition(predictTicks.get());
        return MovementUtil.snapshot().format()
                + ", speedSq=" + formatDouble(MovementUtil.getSpeedSquared())
                + ", predicted" + predictTicks.get() + "=" + formatVec(predicted);
    }

    private String formatVec(Vec3 vec) {
        return "(" + formatDouble(vec.x) + ", " + formatDouble(vec.y) + ", " + formatDouble(vec.z) + ")";
    }

    private String formatDouble(double value) {
        return String.format(Locale.ROOT, "%.3f", value);
    }
}
