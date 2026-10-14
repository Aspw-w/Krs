package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.EntityExtension;
import com.instrumentalist.krs.utils.value.BooleanValue;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import com.instrumentalist.krs.utils.input.Keys;

public class Teams extends Module {

    @Setting
    private static final BooleanValue scoreboardTeam = new BooleanValue("Scoreboard Team", true);

    @Setting
    private static final BooleanValue nameColor = new BooleanValue("Name Color", false);

    @Setting
    private static final BooleanValue prefix = new BooleanValue("Prefix", false);

    @Setting
    private static final BooleanValue armorColor = new BooleanValue("Armor Color", false);

    @Setting
    private static final BooleanValue helmet = new BooleanValue("Helmet", true, armorColor::get);

    @Setting
    private static final BooleanValue chestPlate = new BooleanValue("Chest Plate", true, armorColor::get);

    @Setting
    private static final BooleanValue leggings = new BooleanValue("Leggings", true, armorColor::get);

    @Setting
    private static final BooleanValue boots = new BooleanValue("Boots", true, armorColor::get);

    public Teams() {
        super("Teams", ModuleCategory.Combat, Keys.KEY_UNKNOWN, false, false);
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    public static boolean isInClientPlayersTeam(LivingEntity entity) {
        var player = mc.player;
        if (player == null) return false;

        if (scoreboardTeam.get() && player.isAlliedTo(entity))
            return true;

        Component clientDisplayName = player.getDisplayName();
        Component targetDisplayName = entity.getDisplayName();

        return nameColor.get() && checkName(clientDisplayName, targetDisplayName)
                || prefix.get() && checkPrefix(targetDisplayName, clientDisplayName)
                || armorColor.get() && checkArmor(player, entity);
    }

    private static boolean checkName(Component clientDisplayName, Component targetDisplayName) {
        var targetColor = clientDisplayName.getStyle().getColor();
        var clientColor = targetDisplayName.getStyle().getColor();
        return targetColor != null && clientColor != null && targetColor.equals(clientColor);
    }

    private static boolean checkPrefix(Component targetDisplayName, Component clientDisplayName) {
        String targetName = EntityExtension.stripMinecraftColorCodes(targetDisplayName.getString());
        String clientName = EntityExtension.stripMinecraftColorCodes(clientDisplayName.getString());
        int targetSeparator = targetName.indexOf(' ');
        int clientSeparator = clientName.indexOf(' ');

        return targetSeparator > 0
                && clientSeparator > 0
                && targetName.regionMatches(0, clientName, 0, targetSeparator)
                && targetSeparator == clientSeparator;
    }

    private static boolean checkArmor(Player ownPlayer, LivingEntity entity) {
        if (!(entity instanceof Player player)) return false;

        return helmet.get() && matchesArmorColor(ownPlayer, player, EquipmentSlot.HEAD)
                || chestPlate.get() && matchesArmorColor(ownPlayer, player, EquipmentSlot.CHEST)
                || leggings.get() && matchesArmorColor(ownPlayer, player, EquipmentSlot.LEGS)
                || boots.get() && matchesArmorColor(ownPlayer, player, EquipmentSlot.FEET);
    }

    private static boolean matchesArmorColor(Player ownPlayer, Player player, EquipmentSlot slot) {
        Integer ownColor = EntityExtension.getArmorColor(ownPlayer.getItemBySlot(slot));
        if (ownColor == null) return false;
        Integer otherColor = EntityExtension.getArmorColor(player.getItemBySlot(slot));
        if (otherColor == null) return false;
        return ownColor.equals(otherColor);
    }

}
