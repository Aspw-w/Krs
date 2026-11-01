package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.entity.CombatTargets;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import net.minecraft.world.entity.Entity;

import java.util.Locale;

public class HitBoxes extends Module {
    @Setting
    private static final FloatValue size = new FloatValue("Size", 1f, 0.05f, 3f, "m");

    @Setting
    private static final BooleanValue onlyPlayers = new BooleanValue("Only Players", false);

    public HitBoxes() {
        super("Hit Boxes", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    public static float modifyPickRadius(Entity entity, float original) {
        return original + extraRadius(entity);
    }

    public static float searchExpansion() {
        if (!ModuleManager.getModuleState(HitBoxes.class) || !mc.isSameThread())
            return 0f;
        return size.get();
    }

    public static float extraRadius(Entity entity) {
        if (!ModuleManager.getModuleState(HitBoxes.class) || !mc.isSameThread())
            return 0f;
        if (!CombatTargets.isEnemy(entity, onlyPlayers.get()))
            return 0f;
        return size.get();
    }
}
