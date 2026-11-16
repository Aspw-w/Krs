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
import com.instrumentalist.krs.utils.input.Keys;

import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;

public class Step extends Module {

    public Step() {
        super("Step", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, true);
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
            "x",
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
                } else if (mode.get().equalsIgnoreCase("ncp")) {
                    return (float) Math.min(height.get(), NCP_MAX_HEIGHT);
                } else return height.get();
            }

            if (calledModifiedStep && !mc.player.onGround())
                afterStepFunctions();
        }

        return original;
    }

    private static final double[] NCP_JUMP_ARC = {0.41999998688698, 0.7531999805212, 1.00133597911214, 1.16610926093821, 1.24918707874468};
    private static final double[] NCP_ONE_HALF_BLOCK = {0.41999998688698, 0.7531999805212, 1.00133597911214, 1.16610926093821, 1.24918707874468, 1.1707870772188};
    private static final double[] NCP_TWO_BLOCK = {0.425, 0.821, 0.699, 0.599, 1.022, 1.372, 1.652, 1.869};
    private static final double NCP_MAX_HEIGHT = 2.0;
    private static final double NCP_EPSILON = 1.0E-4;

    private static final double[] HYPIXEL_OFFSETS = {0.41999998688698, 0.7531999805212, 1.001335997911214, 1.16610926093821, 1.24918707874468, 1.093955074228084};
    private static final double VANILLA_STEP_HEIGHT = 0.6;

    public static Vec3 handleCollide(LocalPlayer player, Vec3 movement, Vec3 result) {
        double stepHeight = result.y;
        if (stepHeight <= VANILLA_STEP_HEIGHT + NCP_EPSILON || stepHeight <= movement.y + NCP_EPSILON)
            return result;

        if (steppingFunctions(player, stepHeight))
            return result;

        return Entity.collideBoundingBox(player, movement, player.getBoundingBox(), player.level(), List.of());
    }

    private static double[] ncpOffsets(double stepHeight) {
        double[] table;
        if (stepHeight <= NCP_JUMP_ARC[NCP_JUMP_ARC.length - 1] + NCP_EPSILON)
            table = NCP_JUMP_ARC;
        else if (stepHeight <= 1.5 + NCP_EPSILON)
            table = NCP_ONE_HALF_BLOCK;
        else
            table = NCP_TWO_BLOCK;

        int count = 0;
        while (count < table.length && table[count] < stepHeight - NCP_EPSILON)
            count++;

        return Arrays.copyOf(table, count);
    }

    private static boolean steppingFunctions(LocalPlayer player, double stepHeight) {
        if (!shouldStep()) return true;

        if (!sentBypass) {
            double[] offsets = switch (mode.get().toLowerCase(Locale.ROOT)) {
                case "ncp" -> stepHeight > NCP_MAX_HEIGHT + NCP_EPSILON ? null : ncpOffsets(stepHeight);
                case "hypixel" -> HYPIXEL_OFFSETS;
                default -> new double[0];
            };

            if (offsets == null || !isPathClear(player, offsets))
                return false;

            Vec3 pos = player.position();
            boolean horizontalCollision = player.horizontalCollision;
            for (double offset : offsets)
                sendStepPacket(pos, offset, horizontalCollision);

            sentBypass = true;
        }

        if (customTimer.get())
            TimerUtil.timerSpeed = timerSpeed.get();

        calledModifiedStep = true;
        return true;
    }

    private static boolean isPathClear(LocalPlayer player, double[] offsets) {
        AABB box = player.getBoundingBox();
        for (double offset : offsets) {
            if (!player.level().noCollision(player, box.move(0.0, offset, 0.0)))
                return false;
        }
        return true;
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
