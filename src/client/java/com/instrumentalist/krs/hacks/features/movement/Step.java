package com.instrumentalist.krs.hacks.features.movement;



import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.hacks.features.movement.speed.SpeedModule;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.Vec3;

public class Step extends Module {

    public Step() {
        super("Step", ModuleCategory.Movement, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Setting
    private static final ListValue mode = new ListValue(
            "Mode",
            new String[]{"Vanilla", "NCP", "Hypixel"},
            "Vanilla"
    );

    @Setting
    private static final BooleanValue disableWhenSpeed = new BooleanValue("Disable when Speed", true);

    @Setting
    private static final FloatValue height = new FloatValue(
            "Height",
            2f,
            1f,
            10f,
            () -> !mode.get().equalsIgnoreCase("hypixel")
    );

    @Setting
    private static final IntValue delay = new IntValue(
            "Delay",
            1,
            1,
            10
    );

    @Setting
    private static final BooleanValue customTimer = new BooleanValue("Custom Timer", true);

    @Setting
    private static final FloatValue timerSpeed = new FloatValue(
            "Timer Speed",
            0.6f,
            0.1f,
            10f,
            customTimer::get
    );

    private static boolean canStep = true;
    private static boolean sentBypass = false;
    private static boolean calledModifiedStep = false;
    private static int stepDelay = 0;

    private static boolean shouldStep() {
        return ModuleManager.getModuleState(Step.class) && mc.player != null && !mc.player.isShiftKeyDown() && mc.player.onGround() && canStep && !ModuleManager.getModuleState(FlyModule.class) && (!disableWhenSpeed.get() || !ModuleManager.getModuleState(SpeedModule.class));
    }

    public static float hookStepHeight(float original, LivingEntity entity) {
        if (entity instanceof LocalPlayer && mc.player != null) {
            if (shouldStep()) {
                if (calledModifiedStep)
                    afterStepFunctions();

                if (mode.get().equalsIgnoreCase("hypixel")) {
                    BlockPos above = mc.player.blockPosition().above(3);
                    if (!Client.rotationManager.isRotating()
                            && mc.level.getBlockState(above).isAir()
                            && mc.level.getBlockState(above.east()).isAir()
                            && mc.level.getBlockState(above.south()).isAir()
                            && mc.level.getBlockState(above.west()).isAir()
                            && mc.level.getBlockState(above.north()).isAir())
                        return 1f;
                } else return height.get();
            }

            if (calledModifiedStep && !mc.player.onGround())
                afterStepFunctions();
        }

        return original;
    }

    public static void steppingFunctions() {
        if (!shouldStep()) return;

        if (customTimer.get())
            TimerUtil.timerSpeed = timerSpeed.get();

        if (!sentBypass) {
            Vec3 pos = mc.player.position();
            boolean horizontalCollision = mc.player.horizontalCollision;

            switch (mode.get().toLowerCase(Locale.ROOT)) {
                case "ncp":
                    PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + 0.41999998688698, pos.z, false, horizontalCollision));
                    PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + 0.7531999805212, pos.z, false, horizontalCollision));
                    break;

                case "hypixel":
                    sendStepPacket(pos, .41999998688698, horizontalCollision);
                    sendStepPacket(pos, .7531999805212, horizontalCollision);
                    sendStepPacket(pos, 1.001335997911214, horizontalCollision);
                    sendStepPacket(pos, 1.16610926093821, horizontalCollision);
                    sendStepPacket(pos, 1.24918707874468, horizontalCollision);
                    sendStepPacket(pos, 1.093955074228084, horizontalCollision);
                    break;
            }
            sentBypass = true;
        }

        calledModifiedStep = true;
    }

    private static void sendStepPacket(Vec3 pos, double yOffset, boolean horizontalCollision) {
        PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + yOffset, pos.z, false, horizontalCollision));
    }

    private static void afterStepFunctions() {
        if (customTimer.get())
            TimerUtil.reset();

        stepDelay = delay.get();
        canStep = false;
        sentBypass = false;
        calledModifiedStep = false;
    }

    @Override
    public void onDisable() {
        if (customTimer.get())
            TimerUtil.reset();

        canStep = true;
        stepDelay = 0;
        calledModifiedStep = false;
        sentBypass = false;
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) return;

        if (!canStep) {
            if (stepDelay <= 0) {
                stepDelay = 0;
                canStep = true;
            } else stepDelay--;
        }
    }
}
