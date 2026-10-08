package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.world.BlockUtil;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import org.lwjgl.glfw.GLFW;

public class Xray extends Module {

    @Setting
    private static final BooleanValue bypassAntiXray = new BooleanValue("Bypass Anti Xray", false) {
        @Override
        protected void onChanged(Boolean oldValue, Boolean newValue) {
            super.onChanged(oldValue, newValue);
            refreshIrisShaderPackState();
            reloadChunks();
        }
    };

    private static volatile boolean enabled;
    private static volatile boolean irisShaderPackInUse;

    @Setting
    private static final FloatValue opacity = new FloatValue("Block Opacity", 0.2f, 0f, 0.99f) {
        @Override
        protected void onChanged(Float oldValue, Float newValue) {
            super.onChanged(oldValue, newValue);
            refreshIrisShaderPackState();
            reloadChunks();
        }
    };

    public Xray() {
        super("Xray", ModuleCategory.Level, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
        enabled = false;
        if (mc.level == null) return;
        mc.levelExtractor.allChanged();
    }

    @Override
    public void onEnable() {
        enabled = true;
        refreshIrisShaderPackState();
        if (mc.level == null) return;
        mc.levelExtractor.allChanged();
    }

    public static boolean hookTransparentOre(BlockState blockState, boolean original) {
        if (enabled) {
            if (isVisible(blockState, null)) return true;
            return isOpacityMode() && original;
        }
        return original;
    }

    public static Boolean shouldDrawSide(BlockState blockState, BlockPos pos) {
        return shouldDrawSide(blockState, pos, mc.level);
    }

    public static Boolean shouldDrawSide(BlockState blockState, BlockPos pos, BlockGetter level) {
        if (!enabled) return null;

        if (isVisible(blockState, pos, level)) return true;
        if (isOpacityMode()) return null;

        return false;
    }

    public static boolean isVisible(BlockState blockState, BlockPos pos) {
        return isVisible(blockState, pos, mc.level);
    }

    public static boolean isVisible(BlockState blockState, BlockPos pos, BlockGetter level) {
        if (!isXrayBlock(blockState.getBlock())) return false;
        if (!bypassAntiXray.get() || pos == null || level == null) return true;
        return BlockUtil.INSTANCE.isFullSurroundedBlock(level, pos);
    }

    private static boolean isXrayBlock(Block block) {
        return block == Blocks.COAL_ORE
                || block == Blocks.COPPER_ORE
                || block == Blocks.DIAMOND_ORE
                || block == Blocks.EMERALD_ORE
                || block == Blocks.GOLD_ORE
                || block == Blocks.IRON_ORE
                || block == Blocks.LAPIS_ORE
                || block == Blocks.REDSTONE_ORE
                || block == Blocks.DEEPSLATE_COAL_ORE
                || block == Blocks.DEEPSLATE_COPPER_ORE
                || block == Blocks.DEEPSLATE_DIAMOND_ORE
                || block == Blocks.DEEPSLATE_EMERALD_ORE
                || block == Blocks.DEEPSLATE_GOLD_ORE
                || block == Blocks.DEEPSLATE_IRON_ORE
                || block == Blocks.DEEPSLATE_LAPIS_ORE
                || block == Blocks.DEEPSLATE_REDSTONE_ORE
                || block == Blocks.COAL_BLOCK
                || block == Blocks.COPPER_BLOCK.weathering().unaffected()
                || block == Blocks.DIAMOND_BLOCK
                || block == Blocks.EMERALD_BLOCK
                || block == Blocks.GOLD_BLOCK
                || block == Blocks.IRON_BLOCK
                || block == Blocks.LAPIS_BLOCK
                || block == Blocks.REDSTONE_BLOCK
                || block == Blocks.RAW_COPPER_BLOCK
                || block == Blocks.RAW_GOLD_BLOCK
                || block == Blocks.RAW_IRON_BLOCK
                || block == Blocks.ANCIENT_DEBRIS
                || block == Blocks.NETHER_GOLD_ORE
                || block == Blocks.NETHER_QUARTZ_ORE
                || block == Blocks.NETHERITE_BLOCK
                || block == Blocks.QUARTZ_BLOCK
                || block == Blocks.CHEST
                || block == Blocks.DISPENSER
                || block == Blocks.DROPPER
                || block == Blocks.ENDER_CHEST
                || block == Blocks.HOPPER
                || block == Blocks.TRAPPED_CHEST
                || Blocks.DYED_SHULKER_BOX.asList().contains(block)
                || block == Blocks.SHULKER_BOX
                || block == Blocks.BEACON
                || block == Blocks.CRAFTING_TABLE
                || block == Blocks.ENCHANTING_TABLE
                || block == Blocks.FURNACE
                || block == Blocks.FLOWER_POT
                || block == Blocks.JUKEBOX
                || block == Blocks.LODESTONE
                || block == Blocks.RESPAWN_ANCHOR
                || block == Blocks.ANVIL
                || block == Blocks.CHIPPED_ANVIL
                || block == Blocks.DAMAGED_ANVIL
                || block == Blocks.BARREL
                || block == Blocks.BLAST_FURNACE
                || block == Blocks.BREWING_STAND
                || block == Blocks.CARTOGRAPHY_TABLE
                || block == Blocks.COMPOSTER
                || block == Blocks.FLETCHING_TABLE
                || block == Blocks.GRINDSTONE
                || block == Blocks.LECTERN
                || block == Blocks.LOOM
                || block == Blocks.SMITHING_TABLE
                || block == Blocks.SMOKER
                || block == Blocks.STONECUTTER
                || block == Blocks.CAULDRON
                || block == Blocks.LAVA_CAULDRON
                || block == Blocks.WATER_CAULDRON
                || block == Blocks.LAVA
                || block == Blocks.WATER
                || block == Blocks.END_PORTAL
                || block == Blocks.END_PORTAL_FRAME
                || block == Blocks.NETHER_PORTAL
                || block == Blocks.CHAIN_COMMAND_BLOCK
                || block == Blocks.COMMAND_BLOCK
                || block == Blocks.REPEATING_COMMAND_BLOCK
                || block == Blocks.BOOKSHELF
                || block == Blocks.CLAY
                || block == Blocks.DRAGON_EGG
                || block == Blocks.FIRE
                || block == Blocks.SPAWNER
                || block == Blocks.TNT;
    }

    public static boolean isOpacityMode() {
        return enabled && opacity.get() > 0f && !irisShaderPackInUse;
    }

    public static int getOpacityColorMask() {
        return ((int) (Math.max(0f, Math.min(1f, opacity.get())) * 255f) << 24) | 0xFFFFFF;
    }

    public static float getOpacityFloat() {
        return Math.max(0f, Math.min(1f, opacity.get()));
    }

    private static void reloadChunks() {
        if (mc.level != null && ModuleManager.getModuleState(Xray.class))
            mc.levelExtractor.allChanged();
    }

    private static void refreshIrisShaderPackState() {
        try {
            Class<?> irisApi = Class.forName("net.irisshaders.iris.api.v0.IrisApi");
            Object instance = irisApi.getMethod("getInstance").invoke(null);
            irisShaderPackInUse = (Boolean) irisApi.getMethod("isShaderPackInUse").invoke(instance);
        } catch (Throwable ignored) {
            irisShaderPackInUse = false;
        }
    }

}
