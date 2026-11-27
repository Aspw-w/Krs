package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.entity.CombatTargets;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.input.Keys;
import net.minecraft.world.entity.Entity;

public class Reach extends Module {

    public Reach() {
        super("Reach", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Setting
    private static final BooleanValue block = new BooleanValue("Block", true);

    @Setting
    private static final FloatValue blockReach = new FloatValue("Block Reach", 6f, 0.1f, 6f, "m", block::get);

    @Setting
    private static final BooleanValue entity = new BooleanValue("Entity", true);

    @Setting
    private static final FloatValue entityReach = new FloatValue("Entity Reach", 6f, 0.1f, 6f, "m", entity::get);

    @Setting
    private static final BooleanValue hitBoxes = new BooleanValue("Hit Boxes", false);

    @Setting
    private static final FloatValue hitBoxSize = new FloatValue("Hit Box Size", 1f, 0.05f, 3f, "m", hitBoxes::get);

    @Setting
    private static final BooleanValue hitBoxOnlyPlayers = new BooleanValue("Hit Box Only Players", false, hitBoxes::get);

    public static double hookBlockReach(double original) {
        if (ModuleManager.getModuleState(Reach.class) && block.get())
            return blockReach.get();

        return original;
    }

    public static double hookEntityReach(double original) {
        if (ModuleManager.getModuleState(Reach.class) && entity.get())
            return entityReach.get();

        return original;
    }

    public static float modifyPickRadius(Entity entity, float original) {
        return original + extraRadius(entity);
    }

    public static float searchExpansion() {
        if (!hitBoxesActive())
            return 0f;
        return hitBoxSize.get();
    }

    public static float extraRadius(Entity entity) {
        if (!hitBoxesActive())
            return 0f;
        if (!CombatTargets.isEnemy(entity, hitBoxOnlyPlayers.get()))
            return 0f;
        return hitBoxSize.get();
    }

    private static boolean hitBoxesActive() {
        return ModuleManager.getModuleState(Reach.class) && hitBoxes.get() && mc.isSameThread();
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }
}
