package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.CombatTargets;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.math.RandomUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.level.GameType;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraft.world.phys.HitResult;

public class TriggerBot extends Module {
    @Setting
    private final FloatValue cooldown = new FloatValue("Cooldown", 0.92f, 0.1f, 1f, "x");

    @Setting
    private final IntValue maxReactionTicks = new IntValue("Max Reaction", 2, 0, 6, "ticks");

    @Setting
    private final BooleanValue onlyPlayers = new BooleanValue("Only Players", true);

    @Setting
    private final BooleanValue critTiming = new BooleanValue("Crit Timing", false);

    private Entity aimed;
    private int waitTicks;

    public TriggerBot() {
        super("Trigger Bot", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        aimed = null;
    }

    @Override
    public void onDisable() {
        aimed = null;
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || mc.gameMode == null || mc.gui.screen() != null || player.isUsingItem()
                || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            aimed = null;
            return;
        }

        HitResult hitResult = mc.hitResult;
        if (!(hitResult instanceof EntityHitResult entityHit) || hitResult.getType() != HitResult.Type.ENTITY
                || !CombatTargets.isEnemy(entityHit.getEntity(), onlyPlayers.get())) {
            aimed = null;
            return;
        }

        Entity target = entityHit.getEntity();
        if (target != aimed) {
            aimed = target;
            waitTicks = maxReactionTicks.get() <= 0 ? 0 : RandomUtil.nextInt(0, maxReactionTicks.get() + 1);
        }
        if (waitTicks > 0) {
            waitTicks--;
            return;
        }

        if (player.getAttackStrengthScale(0.5F) < cooldown.get())
            return;
        if (critTiming.get() && !player.onGround() && !player.isInWater() && !player.onClimbable()
                && player.getDeltaMovement().y > -0.08D)
            return;

        mc.gameMode.attack(player, target);
        PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
    }
}
