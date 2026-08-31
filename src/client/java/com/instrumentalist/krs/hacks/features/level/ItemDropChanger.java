package com.instrumentalist.krs.hacks.features.level;

import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.network.protocol.game.ServerboundSwingPacket;
import net.minecraft.world.InteractionHand;
import org.lwjgl.glfw.GLFW;

import java.util.Locale;

public class ItemDropChanger extends Module {

    @Setting
    public static final ListValue mode = new ListValue("Mode", new String[]{"Packet", "No Order"}, "No Order");

    public ItemDropChanger() {
        super("Item Drop Changer", ModuleCategory.Level, GLFW.GLFW_KEY_UNKNOWN, false, false);
    }

    public static boolean hookDropItemSwing(InteractionHand hand) {
        if (ModuleManager.getModuleState(ItemDropChanger.class)) {
            return switch (mode.get().toLowerCase(Locale.ROOT)) {
                case "packet" -> {
                    PacketUtil.sendPacket(new ServerboundSwingPacket(hand));
                    yield false;
                }
                case "no order" -> false;
                default -> true;
            };
        }
        return true;
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }
}
