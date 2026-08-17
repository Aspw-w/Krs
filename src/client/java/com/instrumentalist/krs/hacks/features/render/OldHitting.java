package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.events.features.MouseClickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.combat.KillAura;
import com.instrumentalist.krs.utils.math.ToolUtil;
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

    public static boolean shouldBlock() {
        var player = mc.player;
        if (player == null) return false;
        return mc.level != null
                && ModuleManager.getModuleState(OldHitting.class)
                && ToolUtil.INSTANCE.isSword(player.getMainHandItem())
                && (canBlock && mc.gui.screen() == null || ModuleManager.getModuleState(KillAura.class) && KillAura.isBlocking);
    }

}
