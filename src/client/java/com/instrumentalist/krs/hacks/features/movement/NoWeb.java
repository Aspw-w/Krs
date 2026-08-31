package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import net.minecraft.core.BlockPos;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.phys.AABB;
import org.lwjgl.glfw.GLFW;


public class NoWeb extends Module {
    @Setting
    private final FloatValue speed = new FloatValue("Speed", 0.35f, 0.05f, 1.0f);

    @Setting
    private final BooleanValue vertical = new BooleanValue("Vertical", true);

    public NoWeb() {
        super("No Web", ModuleCategory.Movement, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null || !isInWeb()) return;

        if (MovementUtil.isMoving())
            MovementUtil.setSpeed(Math.max(MovementUtil.getSpeed(), speed.get()));

        if (vertical.get()) {
            if (mc.options.keyJump.isDown())
                MovementUtil.setVelocityY(speed.get());
            else if (mc.options.keyShift.isDown())
                MovementUtil.setVelocityY(-speed.get());
        }
    }

    private boolean isInWeb() {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null)
            return false;

        AABB box = player.getBoundingBox();
        for (int x = (int) Math.floor(box.minX); x <= (int) Math.floor(box.maxX); x++) {
            for (int y = (int) Math.floor(box.minY); y <= (int) Math.floor(box.maxY); y++) {
                for (int z = (int) Math.floor(box.minZ); z <= (int) Math.floor(box.maxZ); z++) {
                    if (level.getBlockState(new BlockPos(x, y, z)).is(Blocks.COBWEB))
                        return true;
                }
            }
        }
        return false;
    }
}
