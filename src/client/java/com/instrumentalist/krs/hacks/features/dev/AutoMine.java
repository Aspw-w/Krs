package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.CancelBlockBreakingEvent;
import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.AutoTool;
import com.instrumentalist.krs.hacks.features.player.Scaffold;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.ToolUtil;
import com.instrumentalist.krs.utils.render.BlockHighlight;
import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.world.QueuedMiner;
import com.instrumentalist.mixin.injector.MultiPlayerGameModeAccessor;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.tags.BlockTags;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;

public class AutoMine extends Module {
    @Setting
    private final BooleanValue onlyOres = new BooleanValue("Only Ores", false);

    @Setting
    private final BooleanValue pauseWhileSneaking = new BooleanValue("Pause While Sneaking", true);

    private static boolean mining;

    private final BlockHighlight highlight = new BlockHighlight(0.2f, RenderColors.AUTO_MINE);
    private BlockPos current;
    private int originalSlot = -1;
    private boolean stopping;

    public AutoMine() {
        super("Auto Mine", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        current = null;
    }

    @Override
    public void onDisable() {
        stop();
        highlight.release();
    }

    @Override
    public void onWorld(WorldEvent event) {
        current = null;
        mining = false;
        originalSlot = -1;
        highlight.release();
    }

    public static boolean shouldSuppressManualMining() {
        return mining && ModuleManager.getModuleState(AutoMine.class);
    }

    @Override
    public void onCancelBlockBreaking(CancelBlockBreakingEvent event) {
        if (!stopping && current != null)
            event.cancel();
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (event.post)
            return;

        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (player == null || level == null || gameMode == null)
            return;

        if (pauseReason(player, gameMode) != null) {
            stop();
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (!(hitResult instanceof BlockHitResult hit) || hit.getType() != HitResult.Type.BLOCK) {
            stop();
            return;
        }

        BlockPos pos = hit.getBlockPos();
        BlockState state = level.getBlockState(pos);
        if (!isMineable(player, level, gameMode, pos, state)) {
            stop();
            return;
        }

        if (current != null && !current.equals(pos))
            restoreSlot();

        current = pos.immutable();
        selectTool(player, current);
        if (ToolSaver.isAboutToBreak(player.getMainHandItem())) {
            stop();
            return;
        }

        if (gameMode.continueDestroyBlock(current, hit.getDirection()))
            PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
        mining = true;

        if (level.getBlockState(current).isAir())
            current = null;
    }

    private String pauseReason(LocalPlayer player, MultiPlayerGameMode gameMode) {
        if (gameMode.getPlayerMode() == GameType.SPECTATOR || player.isSpectator())
            return "Spectator";
        if (mc.gui.screen() != null)
            return "Paused";
        if (player.isUsingItem())
            return "Using item";
        if (pauseWhileSneaking.get() && player.isShiftKeyDown())
            return "Sneaking";
        if (ModuleManager.getModuleState(Scaffold.class))
            return "Scaffold active";
        if (QueuedMiner.shouldSuppressManualMining())
            return "Waiting";
        return null;
    }

    private boolean isMineable(LocalPlayer player, ClientLevel level, MultiPlayerGameMode gameMode, BlockPos pos, BlockState state) {
        if (state.isAir() || state.getBlock() instanceof LiquidBlock || state.getDestroySpeed(level, pos) < 0.0F)
            return false;
        if (player.blockActionRestricted(level, pos, gameMode.getPlayerMode()))
            return false;
        return !onlyOres.get() || state.is(BlockTags.ORES);
    }

    private void stop() {
        MultiPlayerGameMode gameMode = mc.gameMode;
        if (current != null && gameMode != null && ((MultiPlayerGameModeAccessor) gameMode).krs$isDestroying()) {
            stopping = true;
            try {
                gameMode.stopDestroyBlock();
            } finally {
                stopping = false;
            }
        }
        current = null;
        mining = false;
        restoreSlot();
    }

    private void selectTool(LocalPlayer player, BlockPos pos) {
        if (!ModuleManager.getModuleState(AutoTool.class))
            return;

        int bestSlot = ToolUtil.INSTANCE.findBestTool(pos);
        int selected = player.getInventory().getSelectedSlot();
        if (bestSlot == -1 || bestSlot == selected)
            return;

        if (originalSlot == -1)
            originalSlot = selected;
        PlayerUtil.INSTANCE.doSpoof(originalSlot);
        player.getInventory().setSelectedSlot(bestSlot);
    }

    private void restoreSlot() {
        if (originalSlot == -1)
            return;

        LocalPlayer player = mc.player;
        if (player != null && player.getInventory().getSelectedSlot() != originalSlot)
            player.getInventory().setSelectedSlot(originalSlot);
        originalSlot = -1;
        PlayerUtil.INSTANCE.stopSpoof();
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (current == null) {
            highlight.release();
            return;
        }

        highlight.setTarget(current);
    }
}
