package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.utils.value.ListValue;
import org.lwjgl.glfw.GLFW;

public class Blink extends Module {

    @Setting
    public static final ListValue directions = new ListValue("Directions", new String[]{"Both", "Incoming", "Outgoing"}, "Both");

    public Blink() {
        super("Blink", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    public static boolean shouldBlinkIncoming() {
        Blink blink = ModuleManager.getModule(Blink.class);
        if (blink == null) return false;
        String direction = blink.directions.get();
        return direction.equalsIgnoreCase("Both") || direction.equalsIgnoreCase("Incoming");
    }

    public static boolean shouldBlinkOutgoing() {
        Blink blink = ModuleManager.getModule(Blink.class);
        if (blink == null) return false;
        String direction = blink.directions.get();
        return direction.equalsIgnoreCase("Both") || direction.equalsIgnoreCase("Outgoing");
    }

    @Override
    public String tag() {
        return BlinkUtil.INSTANCE.getPacketCount() + "ms";
    }

    @Override
    public void onDisable() {
        BlinkUtil.INSTANCE.sync(true, true);
        BlinkUtil.INSTANCE.stopBlink();
    }

    @Override
    public void onEnable() {
        BlinkUtil.INSTANCE.doBlink();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        BlinkUtil.INSTANCE.doBlink();
    }
}
