package com.instrumentalist.krs.hacks.features.dev;



import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import net.minecraft.client.multiplayer.MultiPlayerGameMode;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public class BlockBreakSimulator2 extends Module {

    public BlockBreakSimulator2() {
        super("Block Break Simulator 2", ModuleCategory.Dev, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
    }

    @Override
    public void onMotion(MotionEvent event) {
        if (mc.player == null) return;
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;

        HitResult ray = mc.player.pick(5.0D, 0.0F, false);
        if (ray.getType() != HitResult.Type.BLOCK) return;

        BlockHitResult blockHit = (BlockHitResult) ray;
        BlockPos pos = blockHit.getBlockPos();
        Direction side = blockHit.getDirection();

        MultiPlayerGameMode interactionManager = mc.gameMode;
        if (interactionManager == null) return;

        interactionManager.continueDestroyBlock(pos, side);
        mc.player.swing(InteractionHand.MAIN_HAND);
    }
}
