package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.packet.BlinkUtil;
import com.instrumentalist.krs.utils.value.IntValue;
import org.lwjgl.glfw.GLFW;

public class InfiniteBlink extends Module {

    @Setting
    private static final IntValue packetsPerRelease = new IntValue("Packets Per Release", 5, 1, 100);

    @Setting
    private static final IntValue ticksBetweenReleases = new IntValue("Ticks Between Releases", 4, 1, 100);

    private int tickCounter = 0;

    public InfiniteBlink() {
        super("InfiniteBlink", ModuleCategory.Player, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String description() {
        return "Blink but only releases a set amount of movement packets every set amount of ticks";
    }

    @Override
    public String tag() {
        return BlinkUtil.INSTANCE.getPacketCount() + " buffered";
    }

    @Override
    public void onDisable() {
        tickCounter = 0;
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
        tickCounter++;
        if (tickCounter >= ticksBetweenReleases.get()) {
            tickCounter = 0;
            BlinkUtil.INSTANCE.releasePackets(packetsPerRelease.get());
        }
    }
}
