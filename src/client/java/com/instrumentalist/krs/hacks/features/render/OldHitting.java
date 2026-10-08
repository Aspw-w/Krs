package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.MouseClickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.KillAura;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.world.entity.HumanoidArm;
import net.minecraft.world.level.block.*;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class OldHitting extends Module {

    @Setting
    public static final ListValue mode = new ListValue("Mode", new String[]{"Vanilla", "Push", "Dash", "Swang", "Swonk"}, "Vanilla");

    @Setting
    private static final ListValue thirdPersonMode = new ListValue("Third Person Mode", new String[]{"Vanilla", "Legacy Vanilla"}, "Legacy Vanilla");

    @Setting
    private static final BooleanValue guardLerp = new BooleanValue("Guard Lerp", true);

    @Setting
    private static final ListValue guardEase = new ListValue("Guard Ease", new String[]{"Linear", "Expo"}, "Expo", guardLerp::get);

    @Setting
    private static final IntValue guardDuration = new IntValue("Guard Duration", 120, 10, 500, "ms", guardLerp::get);

    private static float guardProgress = 0f;
    private static float guardStart = 0f;
    private static float guardTarget = 0f;
    private static float guardTime = 1f;

    private static boolean canBlock = false;

    public OldHitting() {
        super("Old Hitting", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    private boolean noBlockOnSelectedBlock() {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null) return true;

        var hitResult = player.pick(5.0, 0.0f, false);
        if (hitResult.getType() == HitResult.Type.BLOCK) {
            var blockPos = ((BlockHitResult) hitResult).getBlockPos();
            Block block = level.getBlockState(blockPos).getBlock();
            if ((!ModuleManager.getModuleState(KillAura.class) || !KillAura.isBlocking)
                    && (block instanceof ChestBlock || block instanceof EnderChestBlock || block instanceof ShulkerBoxBlock || block instanceof FurnaceBlock
                    || block instanceof CraftingTableBlock || block instanceof CrafterBlock || block instanceof SmokerBlock || block instanceof BlastFurnaceBlock
                    || block instanceof CartographyTableBlock || block instanceof AnvilBlock || block instanceof BellBlock || block instanceof BeaconBlock
                    || block instanceof DragonEggBlock || block instanceof LeverBlock || block instanceof EnchantingTableBlock || block instanceof ButtonBlock
                    || block instanceof GrindstoneBlock || block instanceof LoomBlock || block instanceof NoteBlock || block instanceof FenceGateBlock
                    || block instanceof DoorBlock || block instanceof TrapDoorBlock || block instanceof StonecutterBlock || block instanceof StandingSignBlock
                    || block instanceof WallSignBlock || block instanceof CeilingHangingSignBlock || block instanceof WallHangingSignBlock || block instanceof RepeaterBlock
                    || block instanceof ComparatorBlock || block instanceof DispenserBlock || block instanceof JigsawBlock || block instanceof CommandBlock
                    || block instanceof StructureBlock || block instanceof HopperBlock || block instanceof BedBlock || block instanceof BarrelBlock
                    || block instanceof CakeBlock || block instanceof CandleCakeBlock || block instanceof BrewingStandBlock || block instanceof DaylightDetectorBlock))
                return false;
        }
        return true;
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
        canBlock = false;
        resetGuardProgress();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
        canBlock = false;
    }

    @Override
    public void onMouseClick(MouseClickEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (event.button == GLFW.GLFW_MOUSE_BUTTON_RIGHT) {
            if (event.action == GLFW.GLFW_PRESS && noBlockOnSelectedBlock())
                canBlock = true;

            if (event.action == GLFW.GLFW_RELEASE)
                canBlock = false;
        }
    }

    public static void thirdPersonAnimation(ModelPart arm, HumanoidArm renderArm) {
        int direction = renderArm == HumanoidArm.RIGHT ? 1 : -1;
        switch (thirdPersonMode.get().toLowerCase(Locale.ROOT)) {
            case "vanilla" -> {
                arm.xRot = arm.xRot * 0.5f - 0.9424779f;
                arm.yRot = -0.5f * direction;
                arm.zRot = 0.1f * direction;
            }

            case "legacy vanilla" -> {
                arm.xRot = arm.xRot * 0.5f - 0.9424779f;
                arm.yRot = 0.0f;
                arm.zRot = 0.0f;
            }
        }
    }

    public static boolean shouldUseLegacyVanillaThirdPerson() {
        return thirdPersonMode.get().equalsIgnoreCase("legacy vanilla") && shouldBlock();
    }

    public static float updateGuardProgress() {
        var player = mc.player;
        if (player == null || !ModuleManager.getModuleState(OldHitting.class)
                || !ToolUtil.INSTANCE.isSword(player.getMainHandItem())) {
            resetGuardProgress();
            return 0f;
        }

        float deltaMs = mc.isPaused() ? 0f : Math.min(mc.getDeltaTracker().getGameTimeDeltaTicks() * 50f, 1000f);

        float target = shouldBlock() ? 1f : 0f;
        if (target != guardTarget) {
            guardStart = guardProgress;
            guardTarget = target;
            guardTime = 0f;
        }

        float duration = guardDuration.get() * Math.abs(guardTarget - guardStart);
        guardTime = !guardLerp.get() || duration <= 0f ? 1f : Math.min(1f, guardTime + deltaMs / duration);
        float eased = guardEase.get().equalsIgnoreCase("expo") && guardTime < 1f ? 1f - (float) Math.pow(2, -10 * guardTime) : guardTime;
        guardProgress = guardStart + (guardTarget - guardStart) * eased;
        return guardProgress;
    }

    private static void resetGuardProgress() {
        guardProgress = guardStart = guardTarget = 0f;
        guardTime = 1f;
    }

    public static boolean shouldBlock() {
        var player = mc.player;
        if (player == null) return false;
        return mc.level != null
                && ModuleManager.getModuleState(OldHitting.class)
                && ToolUtil.INSTANCE.isSword(player.getMainHandItem())
                && (canBlock && mc.gui.screen() == null || ModuleManager.getModuleState(KillAura.class) && KillAura.isBlocking);
    }

}
