package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Items;
import com.instrumentalist.krs.utils.input.Keys;

public class AutoLeave extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Health", "Void", "Both", "No Totem", "Smart"}, "Health");

    @Setting
    private final FloatValue health = new FloatValue("Health", 4f, 1f, 20f);

    @Setting
    private final BooleanValue multiplayerOnly = new BooleanValue("Multiplayer Only", true);

    private boolean left = false;

    public AutoLeave() {
        super("Auto Leave", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        left = false;
    }

    @Override
    public void onDisable() {
        left = false;
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        var level = mc.level;
        if (player == null || level == null || left || multiplayerOnly.get() && mc.hasSingleplayerServer())
            return;

        boolean healthDanger = usesHealth() && player.getHealth() <= health.get();
        boolean voidDanger = usesVoid() && player.getY() < level.getMinY() - 8 && !MovementUtil.isBlockBelow();
        boolean totemDanger = usesTotem() && player.getHealth() <= health.get() && !hasTotem();
        if (!healthDanger && !voidDanger && !totemDanger)
            return;

        left = true;
        level.disconnect(Component.literal("Auto Leave"));
    }

    private boolean usesHealth() {
        return mode.get().equalsIgnoreCase("health") || mode.get().equalsIgnoreCase("both") || mode.get().equalsIgnoreCase("smart");
    }

    private boolean usesVoid() {
        return mode.get().equalsIgnoreCase("void") || mode.get().equalsIgnoreCase("both") || mode.get().equalsIgnoreCase("smart");
    }

    private boolean usesTotem() {
        return mode.get().equalsIgnoreCase("no totem") || mode.get().equalsIgnoreCase("smart");
    }

    private boolean hasTotem() {
        var player = mc.player;
        if (player == null)
            return false;

        if (player.getOffhandItem().getItem() == Items.TOTEM_OF_UNDYING)
            return true;

        for (int i = 0; i < player.getInventory().getContainerSize(); i++) {
            if (player.getInventory().getItem(i).getItem() == Items.TOTEM_OF_UNDYING)
                return true;
        }
        return false;
    }
}
