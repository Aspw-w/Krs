package com.instrumentalist.krs.hacks.features.movement.speed;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.hacks.features.movement.speed.features.*;
import com.instrumentalist.krs.utils.value.ListValue;

import java.util.Locale;

public class SpeedModeManager {

    private final ListValue speedMode;
    public SpeedEvent currentMode;

    public SpeedModeManager(ListValue speedMode) {
        this.speedMode = speedMode;
        updateCurrentMode();
    }

    private void updateCurrentMode() {
        switch (speedMode.get().toLowerCase(Locale.ROOT)) {
            case "vanilla":
                currentMode = new VanillaSpeed();
                break;
            case "smooth vanilla":
                currentMode = new SmoothVanillaSpeed();
                break;
            case "hypixel ncp hop":
                currentMode = new HypixelNCPHopSpeed();
                break;
            case "modern mmc":
                currentMode = new ModernMMCSpeed();
                break;
            case "boost":
                currentMode = new BoostSpeed();
                break;
            case "ncp":
                currentMode = new NCPSpeed();
                break;
            case "flag boost":
                currentMode = new FlagBoostSpeed();
                break;
            case "verus":
                currentMode = new VerusSpeed();
                break;
            case "vulcan":
                currentMode = new VulcanSpeed();
                break;
            case "vulcan old":
                currentMode = new VulcanOldSpeed();
                break;
            case "miniblox":
                currentMode = new MinibloxSpeed();
                break;
            default:
                currentMode = null;
        }
    }

    public void onUpdate(UpdateEvent event) {
        SpeedEvent mode = currentMode;
        if (!ModuleManager.getModuleState(FlyModule.class) && mode != null)
            mode.onUpdate(event);
    }

    public void onMotion(MotionEvent event) {
        SpeedEvent mode = currentMode;
        if (!ModuleManager.getModuleState(FlyModule.class) && mode != null)
            mode.onMotion(event);
    }

    public void onTick(TickEvent event) {
        SpeedEvent mode = currentMode;
        if (mode == null || !speedMode.get().equals(mode.getName())) {
            SpeedModule.onDisableFunctions();
            updateCurrentMode();
            SpeedModule.onEnableFunctions();
            mode = currentMode;
        }

        if (!ModuleManager.getModuleState(FlyModule.class) && mode != null)
            mode.onTick(event);
    }

    public void onSendPacket(SendPacketEvent event) {
        SpeedEvent mode = currentMode;
        if (!ModuleManager.getModuleState(FlyModule.class) && mode != null)
            mode.onSendPacket(event);
    }

    public void onReceivedPacket(ReceivedPacketEvent event) {
        SpeedEvent mode = currentMode;
        if (!ModuleManager.getModuleState(FlyModule.class) && mode != null)
            mode.onReceivedPacket(event);
    }
}
