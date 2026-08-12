package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.render.TrueSight;
import net.minecraft.client.Minecraft;
import net.minecraft.core.BlockPos;
import net.minecraft.gizmos.Gizmos;
import net.minecraft.gizmos.GizmoStyle;
import net.minecraft.gizmos.SimpleGizmoCollector;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(net.minecraft.client.renderer.extract.LevelExtractor.class)
public abstract class LevelExtractorMixin {

    @Shadow
    private SimpleGizmoCollector mainThreadGizmos;

    @Inject(method = "extractGizmos", at = @At("HEAD"))
    private void krs$addBarrierGizmos(CallbackInfo ci) {
        Minecraft client = Minecraft.getInstance();
        if (client.player == null || client.level == null)
            return;
        if (!ModuleManager.getModuleState(TrueSight.class) || !TrueSight.barriers.get())
            return;

        BlockPos center = client.player.blockPosition();
        int minY = Math.max(client.level.getMinY(), center.getY() - TrueSight.BARRIER_SCAN_RADIUS_Y);
        int maxY = Math.min(client.level.getMaxY() - 1, center.getY() + TrueSight.BARRIER_SCAN_RADIUS_Y);

        try (Gizmos.TemporaryCollection ignored = Gizmos.withCollector(mainThreadGizmos)) {
            for (BlockPos pos : BlockPos.betweenClosed(
                    center.getX() - TrueSight.BARRIER_SCAN_RADIUS_XZ, minY, center.getZ() - TrueSight.BARRIER_SCAN_RADIUS_XZ,
                    center.getX() + TrueSight.BARRIER_SCAN_RADIUS_XZ, maxY, center.getZ() + TrueSight.BARRIER_SCAN_RADIUS_XZ)) {
                if (client.level.getBlockState(pos).is(Blocks.BARRIER)) {
                    Gizmos.cuboid(
                            new AABB(pos.getX(), pos.getY(), pos.getZ(), pos.getX() + 1, pos.getY() + 1, pos.getZ() + 1),
                            GizmoStyle.stroke(TrueSight.BARRIER_GIZMO_COLOR)
                    );
                }
            }
        }
    }
}
