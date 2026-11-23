package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.input.Keys;
import net.minecraft.util.Mth;

public class ServerRotations extends Module {
    @Setting
    public static final BooleanValue vanillaBody = new BooleanValue("Vanilla Body", true);

    @Setting
    public static final BooleanValue capeMovementCorrection = new BooleanValue("Cape Movement Correction", true);

    @Setting
    public static final BooleanValue headInterpolation = new BooleanValue("Head Interpolation", true);

    private static float prevHeadPitch;
    private static float headPitch;
    private static boolean headPitchReady = false;

    public ServerRotations() {
        super("Server Rotations", ModuleCategory.Render, Keys.KEY_UNKNOWN, true, false);
    }

    public static boolean shouldUseVanilla() {
        return ModuleManager.getModuleState(ServerRotations.class) && vanillaBody.get();
    }

    public static boolean shouldInterpolateHead() {
        return headPitchReady && headInterpolation.get() && ModuleManager.getModuleState(ServerRotations.class);
    }

    public static float getInterpolatedHeadPitch(float partialTicks) {
        return Mth.lerp(partialTicks, prevHeadPitch, headPitch);
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) {
            headPitchReady = false;
            return;
        }

        float pitch = mc.player.getXRot();
        if (!headPitchReady) {
            prevHeadPitch = pitch;
            headPitch = pitch;
            headPitchReady = true;
            return;
        }

        prevHeadPitch = headPitch;
        headPitch = pitch;
    }

    @Override
    public void onWorld(WorldEvent event) {
        headPitchReady = false;
    }

    @Override
    public void onDisable() {
        headPitchReady = false;
    }

    @Override
    public void onEnable() {
        headPitchReady = false;
    }
}
