package com.instrumentalist.krs.hacks.features.movement.speed;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.features.movement.speed.features.*;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.mojang.blaze3d.platform.InputConstants;
import org.lwjgl.glfw.GLFW;

public class SpeedModule extends Module {

    public SpeedModule() {
        super("Speed", ModuleCategory.Movement, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Setting
    private static final ListValue speedMode = new ListValue(
            "Speed Mode",
            new String[]{"Vanilla", "Smooth Vanilla", "Hypixel NCP Hop", "Modern MMC", "Boost", "Flag Boost", "NCP", "Verus", "Vulcan", "Vulcan Old", "Miniblox"},
            "Vanilla"
    );

    private static final SpeedModeManager speedModeManager = new SpeedModeManager(speedMode);

    // Identify
    @Setting
    public static final FloatValue vanillaSpeed = new FloatValue("Speed", 1f, 0.1f, 10f, () -> speedMode.get().equalsIgnoreCase("vanilla") || speedMode.get().equalsIgnoreCase("smooth vanilla") || speedMode.get().equalsIgnoreCase("flag boost"));

    // Vanilla
    @Setting
    public static final BooleanValue vanillaAutoBHop = new BooleanValue("Auto BHop", true, () -> speedMode.get().equalsIgnoreCase("vanilla"));

    // Vulcan Old
    @Setting
    public static final ListValue vulcan2Mode = new ListValue("Vulcan Old Mode", new String[]{"BHop", "LowHop", "Ground", "YPort"}, "BHop", () -> speedMode.get().equalsIgnoreCase("vulcan old"));

    public static void onEnableFunctions() {
        if (speedModeManager.currentMode instanceof VulcanOldSpeed)
            VulcanOldSpeed.reset();
    }

    public static void onDisableFunctions() {
        if (speedModeManager.currentMode instanceof VanillaSpeed) {
            if (mc.player != null)
                MovementUtil.stopMoving();
            VanillaSpeed.cancelledOnce = false;
        } else if (speedModeManager.currentMode instanceof FlagBoostSpeed) {
            FlagBoostSpeed.tick1 = 0;
            if (mc.player != null)
                MovementUtil.stopXZ();
        } else if (speedModeManager.currentMode instanceof MinibloxSpeed) {
            if (mc.player != null)
                MovementUtil.stopMoving();
        } else if (speedModeManager.currentMode instanceof HypixelNCPHopSpeed) {
            HypixelNCPHopSpeed.canLowHop = false;
        } else if (speedModeManager.currentMode instanceof VulcanOldSpeed) {
            if (SpeedModule.vulcan2Mode.get().equalsIgnoreCase("ground") && mc.player != null)
                VulcanOldSpeed.strafe(MovementUtil.getBaseMoveSpeed(0.2873) * 0.6 - 0.02);

            VulcanOldSpeed.reset();
        } else if (speedModeManager.currentMode instanceof NCPSpeed) {
            if (mc.player != null)
                MovementUtil.stopMoving();
        } else if (speedModeManager.currentMode instanceof VerusSpeed) {
            if (mc.player != null)
                MovementUtil.stopMoving();
        } else if (speedModeManager.currentMode instanceof BoostSpeed) {
            if (mc.player != null)
                MovementUtil.stopMoving();
            BoostSpeed.boostSpeed = 0f;
        }

        if (mc.player != null) {
            mc.options.keyJump.setDown(InputConstants.isKeyDown(mc.getWindow(), InputConstants.getKey(mc.options.keyJump.saveString()).getValue()));
        }
    }

    @Override
    public String tag() {
        return speedMode.get();
    }

    @Override
    public void onDisable() {
        onDisableFunctions();
    }

    @Override
    public void onEnable() {
        onEnableFunctions();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        speedModeManager.onUpdate(event);
    }

    @Override
    public void onMotion(MotionEvent event) {
        speedModeManager.onMotion(event);
    }

    @Override
    public void onTick(TickEvent event) {
        speedModeManager.onTick(event);
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        speedModeManager.onSendPacket(event);
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        speedModeManager.onReceivedPacket(event);
    }
}
