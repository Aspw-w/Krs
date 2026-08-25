package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.item.BlockItem;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import org.lwjgl.glfw.GLFW;

public class AirPlace extends Module {

    public AirPlace() {
        super("Air Place", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    public static HitResult createPlacementHit(HitResult original, LocalPlayer player) {
        if (!ModuleManager.getModuleState(AirPlace.class)
                || original == null
                || original.getType() != HitResult.Type.MISS
                || !(original instanceof BlockHitResult miss)
                || player == null
                || !(player.getMainHandItem().getItem() instanceof BlockItem)
                && !(player.getOffhandItem().getItem() instanceof BlockItem)) {
            return original;
        }

        BlockPos target = BlockPos.containing(miss.getLocation());
        return new BlockHitResult(miss.getLocation(), miss.getDirection(), target, false);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }
}
