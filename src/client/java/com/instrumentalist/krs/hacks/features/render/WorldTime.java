package com.instrumentalist.krs.hacks.features.render;



import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ListValue;
import com.instrumentalist.krs.utils.input.Keys;


public class WorldTime extends Module {

    public WorldTime() {
        super("World Time", ModuleCategory.Render, Keys.KEY_UNKNOWN, false, false);
    }

    @Setting
    private static final ListValue time = new ListValue(
            "Time of Day",
            new String[]{"Day", "Night", "Midnight", "Sunrise"},
            "Midnight"
    );

    @Setting
    public static final BooleanValue clearWeather = new BooleanValue("Clear Weather", true);

    private static final Long[] TIME_TICKS = {1000L, 13000L, 18000L, 0L};

    public static Long getActiveTimeTicks() {
        if (!ModuleManager.getModuleState(WorldTime.class) || mc.level == null)
            return null;

        int index = time.getCurrentIndex();
        return index >= 0 && index < TIME_TICKS.length ? TIME_TICKS[index] : null;
    }

    public static boolean shouldClearWeather() {
        return ModuleManager.getModuleState(WorldTime.class) && clearWeather.get();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }
}
