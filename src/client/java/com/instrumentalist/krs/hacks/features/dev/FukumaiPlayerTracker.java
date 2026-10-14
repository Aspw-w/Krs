package com.instrumentalist.krs.hacks.features.dev;



import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.math.MSTimer;
import com.instrumentalist.krs.utils.nanovg.NVGFonts;
import com.instrumentalist.krs.utils.network.WebAccessUtil;
import com.instrumentalist.krs.utils.input.Keys;
import org.nvgu.util.Alignment;

import java.awt.*;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

public class FukumaiPlayerTracker extends Module {

    public FukumaiPlayerTracker() {
        super("Fukumai Player Tracker", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    private static volatile List<WebAccessUtil.PlayerInfo> playerInfoList = null;
    private static final MSTimer timer = new MSTimer();
    private static final AtomicLong requestGeneration = new AtomicLong();

    @Override
    public void onDisable() {
        clearList();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onWorld(WorldEvent event) {
        clearList();
    }

    private void clearList() {
        requestGeneration.incrementAndGet();
        playerInfoList = null;
    }

    @Override
    public void onTick(TickEvent event) {
        if (mc.player == null || mc.level == null) return;

        if (timer.hasTimePassed(1000L)) {
            long generation = requestGeneration.get();
            WebAccessUtil.getFukumaiPlayersInfoAsync(result -> {
                if (Client.loaded && result != null && generation == requestGeneration.get())
                    playerInfoList = List.copyOf(result);
            });
            timer.reset();
        }
    }

    @Override
    public void onRenderHud(RenderHudEvent event) {
        List<WebAccessUtil.PlayerInfo> players = playerInfoList;
        if (mc.player == null || mc.level == null || players == null || players.isEmpty()) return;

        Client.nanoVgManager.load(vg -> {
            float startX = 400f;
            float posY = 30f;
            float indent = 20f;

            NVGFonts.INTER_MEDIUM.drawText("Timer=" + timer.currentTime() + "/1000", startX, posY, 18f, Color.WHITE, Alignment.LEFT_TOP, true);
            posY += 18f + 10f;

            for (WebAccessUtil.PlayerInfo playerInfo : players) {
                NVGFonts.INTER_MEDIUM.drawText("Name: " + playerInfo.getName(), startX, posY, 18f, Color.WHITE, Alignment.LEFT_TOP, true);
                posY += 18f;
                NVGFonts.INTER_MEDIUM.drawText("Health: " + playerInfo.getHealth(), startX + indent, posY, 16f, Color.LIGHT_GRAY, Alignment.LEFT_TOP, true);
                posY += 16f;
                NVGFonts.INTER_MEDIUM.drawText("Position: X=" + playerInfo.getX() + ", Y=" + playerInfo.getY() + ", Z=" + playerInfo.getZ(), startX + indent, posY, 16f, Color.LIGHT_GRAY, Alignment.LEFT_TOP, true);
                posY += 18f + 8f;
            }
        });
    }
}
