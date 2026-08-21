package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.mixin.injector.IMinecraftClient;
import net.minecraft.world.item.BlockItem;
import org.lwjgl.glfw.GLFW;

public class FastPlace extends Module {

    @Setting
    private static final FloatValue delay = new FloatValue("Delay", 0f, 0f, 4f);

    public FastPlace() {
        super("Fast Place", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.options == null)
            return;

        boolean holdingBlock = mc.player.getMainHandItem().getItem() instanceof BlockItem
                || mc.player.getOffhandItem().getItem() instanceof BlockItem;

        if (holdingBlock && mc.options.keyUse.isDown()) {
            int target = (int) Math.max(0f, delay.get());
            int current = ((IMinecraftClient) mc).krs$getRightClickDelay();
            if (current > target)
                ((IMinecraftClient) mc).krs$setRightClickDelay(target);
        }
    }
}
