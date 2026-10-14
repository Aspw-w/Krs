package com.instrumentalist.krs.hacks.features.player;



import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.input.Keys;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;

public class MurdererDetector extends Module {

    @Setting
    private static final BooleanValue chatAbuse = new BooleanValue("Chat Abuse", false);

    public MurdererDetector() {
        super("Murderer Detector", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    public static ArrayList<Player> murderers = new ArrayList<>();
    private static final Set<UUID> murdererIds = new HashSet<>();

    @Override
    public void onDisable() {
        murderers.clear();
        murdererIds.clear();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
        murderers.clear();
        murdererIds.clear();
    }

    public static boolean isMurderer(Entity entity) {
        return entity instanceof Player && murdererIds.contains(entity.getUUID());
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null || mc.level == null) return;

        for (Player player : mc.level.players()) {
            UUID uuid = player.getUUID();
            if (murdererIds.contains(uuid)) {
                trackMurderer(player);
                continue;
            }

            ItemStack mainHandItem = player.getMainHandItem();
            if (mainHandItem.isEmpty()) continue;

            boolean suspiciousItem = isMurdererItem(mainHandItem.getItem());
            boolean knifeName = !suspiciousItem && mainHandItem.getHoverName().getString().equalsIgnoreCase("knife");
            if (!suspiciousItem && !knifeName) continue;

            String playerName = player.getName().getString();
            if (!playerName.isBlank()) {
                murdererIds.add(uuid);
                trackMurderer(player);
                if (chatAbuse.get() && !mc.player.getName().getString().equalsIgnoreCase(playerName)) {
                    mc.player.connection.sendChat(playerName + " is a murderer!!!!!!!!");
                }
                Client.notificationManager.addNotification("Murderer detected!", "Murderer " + playerName + " was detected!");
                ChatUtil.printChat("Murderer " + playerName + " was detected!");
            }
        }
    }

    private static void trackMurderer(Player player) {
        UUID uuid = player.getUUID();
        int existingIndex = -1;

        for (int i = 0; i < murderers.size(); i++) {
            Player murderer = murderers.get(i);
            if (!murderer.getUUID().equals(uuid)) continue;

            if (existingIndex == -1) {
                existingIndex = i;
                continue;
            }

            murderers.remove(i);
            i--;
        }

        if (existingIndex != -1) {
            murderers.set(existingIndex, player);
            return;
        }

        murderers.add(player);
    }

    private static boolean isMurdererItem(Item item) {
        return item == Items.IRON_SWORD
                || item == Items.ENDER_CHEST
                || item == Items.STONE_SWORD
                || item == Items.IRON_SHOVEL
                || item == Items.STICK
                || item == Items.WOODEN_AXE
                || item == Items.WOODEN_SWORD
                || item == Items.DEAD_BUSH
                || item == Items.SUGAR_CANE
                || item == Items.STONE_SHOVEL
                || item == Items.BLAZE_ROD
                || item == Items.DIAMOND_SHOVEL
                || item == Items.QUARTZ
                || item == Items.PUMPKIN_PIE
                || item == Items.GOLDEN_PICKAXE
                || item == Items.LEAD
                || item == Items.NAME_TAG
                || item == Items.CHARCOAL
                || item == Items.FLINT
                || item == Items.BONE
                || item == Items.CARROT
                || item == Items.GOLDEN_CARROT
                || item == Items.COOKIE
                || item == Items.DIAMOND_AXE
                || item == Items.ROSE_BUSH
                || item == Items.PRISMARINE_SHARD
                || item == Items.COOKED_BEEF
                || item == Items.NETHER_BRICK
                || item == Items.COOKED_CHICKEN
                || item == Items.MUSIC_DISC_BLOCKS
                || item == Items.GOLDEN_HOE
                || item == Items.LAPIS_LAZULI
                || item == Items.GOLDEN_SWORD
                || item == Items.DIAMOND_SWORD
                || item == Items.DIAMOND_HOE
                || item == Items.SHEARS
                || item == Items.SALMON
                || item == Items.DYE.red()
                || item == Items.BREAD
                || item == Items.OAK_BOAT
                || item == Items.GLISTERING_MELON_SLICE
                || item == Items.BOOK
                || item == Items.JUNGLE_SAPLING
                || item == Items.GOLDEN_AXE
                || item == Items.DIAMOND_PICKAXE
                || item == Items.GOLDEN_SHOVEL;
    }
}
