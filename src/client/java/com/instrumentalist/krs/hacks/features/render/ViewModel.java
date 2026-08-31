package com.instrumentalist.krs.hacks.features.render;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import org.lwjgl.glfw.GLFW;


public class ViewModel extends Module {

    @Setting
    public static final FloatValue itemSize = new FloatValue("Item Size", 0f, -2f, 2f);

    @Setting
    public static final FloatValue itemFov = new FloatValue("Item Fov", 0f, -2f, 2f);

    @Setting
    public static final FloatValue itemPositionX = new FloatValue("Item Position X", 0f, -2f, 2f);

    @Setting
    public static final FloatValue itemPositionY = new FloatValue("Item Position Y", 0f, -2f, 2f);

    @Setting
    public static final FloatValue itemPositionZ = new FloatValue("Item Position Z", 0f, -2f, 2f);

    @Setting
    public static final BooleanValue noEquipAnimation = new BooleanValue("No Equip Animation", false);

    @Setting
    public static final BooleanValue fluxSwing = new BooleanValue("Flux Swing", false);

    @Setting
    public static final BooleanValue oldEatSwing = new BooleanValue("Old Eat Swing", false);

    @Setting
    public static final BooleanValue oldItemPosition = new BooleanValue("Old Item Position", false);

    @Setting
    public static final BooleanValue punchingABlockWhileDoingStuff = new BooleanValue("Punching a block while doing stuff", false);

    @Setting
    private static final BooleanValue slowSwing = new BooleanValue("Slow Swing", false);

    @Setting
    private static final IntValue setSwingSpeed = new IntValue("Set Swing Speed", 5, -5, 20, slowSwing::get);

    public ViewModel() {
        super("View Model", ModuleCategory.Render, GLFW.GLFW_KEY_UNKNOWN, true, false);
    }

    @Override
    public void onDisable() {
        if (Client.loaded)
            this.toggle();
    }

    @Override
    public void onEnable() {
    }

    public static int hookSwingSpeed(int original, Entity entity) {
        if (ModuleManager.getModuleState(ViewModel.class) && slowSwing.get() && entity instanceof LocalPlayer)
            return 6 + setSwingSpeed.get();

        return original;
    }

}
