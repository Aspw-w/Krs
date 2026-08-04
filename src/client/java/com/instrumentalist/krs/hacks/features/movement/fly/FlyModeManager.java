package com.instrumentalist.krs.hacks.features.movement.fly;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.features.movement.fly.features.*;
import com.instrumentalist.krs.utils.value.ListValue;

import java.util.Locale;

public class FlyModeManager {

    private final ListValue flyMode;
    public FlyEvent currentMode;

    public FlyModeManager(ListValue flyMode) {
        this.flyMode = flyMode;
        updateCurrentMode();
    }

    private void updateCurrentMode() {
        switch (flyMode.get().toLowerCase(Locale.ROOT)) {
            case "vanilla":
                currentMode = new VanillaFly();
                break;
            case "jetpack":
                currentMode = new JetpackFly();
                break;
            case "matrix":
                currentMode = new MatrixFly();
                break;
            case "grim 1.9-1.18.1":
                currentMode = new Grim19To1181Fly();
                break;
            case "creative":
                currentMode = new CreativeFly();
                break;
            case "airwalk":
                currentMode = new AirwalkFly();
                break;
            case "negative packet":
                currentMode = new NegativePacketFly();
                break;
            case "2b2tjp control fast":
                currentMode = new TwoBTwoTJPControlFastFly();
                break;
            case "modern mmc":
                currentMode = new ModernMMCFly();
                break;
            case "vulcan glide":
                currentMode = new VulcanGlideFly();
                break;
            case "verus jetpack":
                currentMode = new VerusJetpackFly();
                break;
            case "float":
                currentMode = new FloatFly();
                break;
            case "ncp boost":
                currentMode = new NCPBoostFly();
                break;
            default:
                currentMode = null;
        }
    }

    public void onUpdate(UpdateEvent event) {
        FlyEvent mode = currentMode;
        if (mode != null)
            mode.onUpdate(event);
    }

    public void onMotion(MotionEvent event) {
        FlyEvent mode = currentMode;
        if (mode != null)
            mode.onMotion(event);
    }

    public void onTick(TickEvent event) {
        FlyEvent mode = currentMode;
        if (mode == null || !flyMode.get().equals(mode.getName())) {
            FlyModule.onDisableFunctions();
            updateCurrentMode();
            FlyModule.onEnableFunctions();
            mode = currentMode;
        }

        if (mode != null)
            mode.onTick(event);
    }

    public void onSendPacket(SendPacketEvent event) {
        FlyEvent mode = currentMode;
        if (mode != null)
            mode.onSendPacket(event);
    }

    public void onReceivedPacket(ReceivedPacketEvent event) {
        FlyEvent mode = currentMode;
        if (mode != null)
            mode.onReceivedPacket(event);
    }

    public void onBlock(BlockEvent event) {
        FlyEvent mode = currentMode;
        if (mode != null)
            mode.onBlock(event);
    }
}
