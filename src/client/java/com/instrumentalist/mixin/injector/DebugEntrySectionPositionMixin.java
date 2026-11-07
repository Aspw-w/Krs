package com.instrumentalist.mixin.injector;

import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.player.Freecam;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.components.debug.DebugEntryPosition;
import net.minecraft.client.gui.components.debug.DebugEntrySectionPosition;
import net.minecraft.client.gui.components.debug.DebugScreenDisplayer;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.chunk.LevelChunk;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.Locale;

@Mixin(DebugEntrySectionPosition.class)
public abstract class DebugEntrySectionPositionMixin {
    @Inject(method = "display", at = @At("HEAD"), cancellable = true)
    private void freecamSectionPositionHook(DebugScreenDisplayer displayer, Level serverOrClientLevel, LevelChunk clientChunk, LevelChunk serverChunk, CallbackInfo ci) {
        if (!ModuleManager.getModuleState(Freecam.class) || !Freecam.getCanFly())
            return;

        ci.cancel();
        BlockPos blockPos = BlockPos.containing(Freecam.getCamPos(TimerUtil.selfPartialTick(Minecraft.getInstance().getDeltaTracker().getGameTimeDeltaPartialTick(true))));
        displayer.addToGroup(
                DebugEntryPosition.GROUP,
                String.format(Locale.ROOT, "Section-relative: %02d %02d %02d", blockPos.getX() & 15, blockPos.getY() & 15, blockPos.getZ() & 15)
        );
    }
}
