package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.value.BooleanValue;
import net.minecraft.network.protocol.game.ClientboundUpdateMobEffectPacket;
import net.minecraft.world.effect.MobEffects;
import com.instrumentalist.krs.utils.input.Keys;

public class AntiEffects extends Module {

    @Setting
    private final BooleanValue antiLevitation = new BooleanValue("Anti Levitation", false);

    @Setting
    private static final BooleanValue noPush = new BooleanValue("No Push", false);

    @Setting
    private final BooleanValue noJumpCooldown = new BooleanValue("No Jump Cooldown", false);

    public AntiEffects() {
        super("Anti Effects", ModuleCategory.Movement, Keys.KEY_UNKNOWN, false, false);
    }

    public static boolean isNoPushActive() {
        return ModuleManager.getModuleState(AntiEffects.class) && noPush.get();
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        var player = mc.player;
        if (!antiLevitation.get() || player == null || !(event.packet instanceof ClientboundUpdateMobEffectPacket packet))
            return;

        if (packet.getEntityId() == player.getId() && packet.getEffect() == MobEffects.LEVITATION)
            event.cancel();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        var player = mc.player;
        if (player == null) return;

        if (antiLevitation.get() && player.hasEffect(MobEffects.LEVITATION))
            player.removeEffect(MobEffects.LEVITATION);

        if (noJumpCooldown.get() && player.onGround())
            player.noJumpDelay = 0;
    }
}
