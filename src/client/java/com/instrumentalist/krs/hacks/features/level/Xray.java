package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.world.BlockUtil;
import it.unimi.dsi.fastutil.objects.ReferenceOpenHashSet;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.BlockGetter;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import com.instrumentalist.krs.utils.input.Keys;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.Arrays;
import java.util.Set;

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
        super("Xray", ModuleCategory.Level, Keys.KEY_UNKNOWN, false, true);
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

    public static boolean isActive() {
        return enabled;
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

    private static volatile Set<Block> xrayBlocks;

    private static boolean isXrayBlock(Block block) {
        Set<Block> blocks = xrayBlocks;
        if (blocks == null)
            xrayBlocks = blocks = createXrayBlocks();
        return blocks.contains(block);
    }

    private static Set<Block> createXrayBlocks() {
        Set<Block> blocks = new ReferenceOpenHashSet<>(Arrays.asList(
                Blocks.COAL_ORE,
                Blocks.COPPER_ORE,
                Blocks.DIAMOND_ORE,
                Blocks.EMERALD_ORE,
                Blocks.GOLD_ORE,
                Blocks.IRON_ORE,
                Blocks.LAPIS_ORE,
                Blocks.REDSTONE_ORE,
                Blocks.DEEPSLATE_COAL_ORE,
                Blocks.DEEPSLATE_COPPER_ORE,
                Blocks.DEEPSLATE_DIAMOND_ORE,
                Blocks.DEEPSLATE_EMERALD_ORE,
                Blocks.DEEPSLATE_GOLD_ORE,
                Blocks.DEEPSLATE_IRON_ORE,
                Blocks.DEEPSLATE_LAPIS_ORE,
                Blocks.DEEPSLATE_REDSTONE_ORE,
                Blocks.COAL_BLOCK,
                Blocks.COPPER_BLOCK.weathering().unaffected(),
                Blocks.DIAMOND_BLOCK,
                Blocks.EMERALD_BLOCK,
                Blocks.GOLD_BLOCK,
                Blocks.IRON_BLOCK,
                Blocks.LAPIS_BLOCK,
                Blocks.REDSTONE_BLOCK,
                Blocks.RAW_COPPER_BLOCK,
                Blocks.RAW_GOLD_BLOCK,
                Blocks.RAW_IRON_BLOCK,
                Blocks.ANCIENT_DEBRIS,
                Blocks.NETHER_GOLD_ORE,
                Blocks.NETHER_QUARTZ_ORE,
                Blocks.NETHERITE_BLOCK,
                Blocks.QUARTZ_BLOCK,
                Blocks.CHEST,
                Blocks.DISPENSER,
                Blocks.DROPPER,
                Blocks.ENDER_CHEST,
                Blocks.HOPPER,
                Blocks.TRAPPED_CHEST,
                Blocks.SHULKER_BOX,
                Blocks.BEACON,
                Blocks.CRAFTING_TABLE,
                Blocks.ENCHANTING_TABLE,
                Blocks.FURNACE,
                Blocks.FLOWER_POT,
                Blocks.JUKEBOX,
                Blocks.LODESTONE,
                Blocks.RESPAWN_ANCHOR,
                Blocks.ANVIL,
                Blocks.CHIPPED_ANVIL,
                Blocks.DAMAGED_ANVIL,
                Blocks.BARREL,
                Blocks.BLAST_FURNACE,
                Blocks.BREWING_STAND,
                Blocks.CARTOGRAPHY_TABLE,
                Blocks.COMPOSTER,
                Blocks.FLETCHING_TABLE,
                Blocks.GRINDSTONE,
                Blocks.LECTERN,
                Blocks.LOOM,
                Blocks.SMITHING_TABLE,
                Blocks.SMOKER,
                Blocks.STONECUTTER,
                Blocks.CAULDRON,
                Blocks.LAVA_CAULDRON,
                Blocks.WATER_CAULDRON,
                Blocks.LAVA,
                Blocks.WATER,
                Blocks.END_PORTAL,
                Blocks.END_PORTAL_FRAME,
                Blocks.NETHER_PORTAL,
                Blocks.CHAIN_COMMAND_BLOCK,
                Blocks.COMMAND_BLOCK,
                Blocks.REPEATING_COMMAND_BLOCK,
                Blocks.BOOKSHELF,
                Blocks.CLAY,
                Blocks.DRAGON_EGG,
                Blocks.FIRE,
                Blocks.SPAWNER,
                Blocks.TNT
        ));
        blocks.addAll(Blocks.DYED_SHULKER_BOX.asList());
        return blocks;
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
