package com.instrumentalist.krs.hacks.features.dev;

import com.instrumentalist.krs.Client;
import com.instrumentalist.krs.events.features.Render3DEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.entity.CombatTargets;
import com.instrumentalist.krs.utils.entity.ExplosionDamage;
import com.instrumentalist.krs.utils.entity.PlayerUtil;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.render.BlockHighlight;
import com.instrumentalist.krs.utils.render.RenderColors;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.FloatValue;
import com.instrumentalist.krs.utils.value.IntValue;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.util.Mth;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.boss.enderdragon.EndCrystal;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.item.Items;
import net.minecraft.world.level.GameType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.Vec3;

import java.util.Locale;

public class CrystalAura extends Module {
    private static final int ATTACK_RETRY_TICKS = 4;
    private static final float LETHAL_MARGIN = 0.5F;

    @Setting
    private final FloatValue targetRange = new FloatValue("Target Range", 10f, 4f, 16f, "m");

    @Setting
    private final FloatValue placeRange = new FloatValue("Place Range", 4.5f, 1f, 6f, "m");

    @Setting
    private final FloatValue breakRange = new FloatValue("Break Range", 4.5f, 1f, 6f, "m");

    @Setting
    private final FloatValue minDamage = new FloatValue("Min Damage", 6f, 0f, 20f, "hp");

    @Setting
    private final FloatValue maxSelfDamage = new FloatValue("Max Self Damage", 8f, 0f, 20f, "hp");

    @Setting
    private final BooleanValue antiSuicide = new BooleanValue("Anti Suicide", true);

    @Setting
    private final IntValue placeDelay = new IntValue("Place Delay", 1, 0, 10, "ticks");

    @Setting
    private final IntValue breakDelay = new IntValue("Break Delay", 1, 0, 10, "ticks");

    @Setting
    private final BooleanValue place = new BooleanValue("Place", true);

    @Setting
    private final BooleanValue breakCrystals = new BooleanValue("Break", true);

    @Setting
    private final BooleanValue autoSwitch = new BooleanValue("Auto Switch", true);

    @Setting
    private final BooleanValue rotations = new BooleanValue("Rotations", true);

    @Setting
    private final BooleanValue onlyPlayers = new BooleanValue("Only Players", true);

    private final Int2IntOpenHashMap attacked = new Int2IntOpenHashMap();
    private final BlockHighlight highlight = new BlockHighlight(0.25f, RenderColors.CRYSTAL_AURA);
    private LivingEntity target;
    private BlockPos lastPlaced;
    private float lastDamage;
    private int placeCooldown;
    private int breakCooldown;
    private boolean rotating;

    public CrystalAura() {
        super("Crystal Aura", ModuleCategory.Dev, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return target == null ? null : target.getName().getString() + " " + String.format(Locale.ROOT, "%.1f", lastDamage);
    }

    @Override
    public void onEnable() {
        clear();
    }

    @Override
    public void onDisable() {
        clear();
        stopRotating();
    }

    @Override
    public void onWorld(WorldEvent event) {
        clear();
        rotating = false;
    }

    private void clear() {
        attacked.clear();
        target = null;
        lastPlaced = null;
        lastDamage = 0f;
        placeCooldown = 0;
        breakCooldown = 0;
        highlight.release();
    }

    private void stopRotating() {
        if (rotating) {
            Client.rotationManager.stopRotation();
            rotating = false;
        }
    }

    @Override
    public void onTick(TickEvent event) {
        LocalPlayer player = mc.player;
        ClientLevel level = mc.level;
        if (player == null || level == null || mc.gameMode == null || mc.gameMode.getPlayerMode() == GameType.SPECTATOR) {
            target = null;
            stopRotating();
            return;
        }

        target = CombatTargets.nearest(targetRange.get(), onlyPlayers.get());
        if (target == null || mc.gui.screen() != null) {
            lastPlaced = null;
            stopRotating();
            return;
        }

        int now = player.tickCount;
        attacked.int2IntEntrySet().removeIf(entry -> now - entry.getIntValue() > 40);
        placeCooldown--;
        breakCooldown--;

        boolean acted = false;
        if (breakCrystals.get() && breakCooldown <= 0 && breakBest(player, level)) {
            breakCooldown = breakDelay.get();
            acted = true;
        }
        if (place.get() && placeCooldown <= 0 && placeBest(player, level)) {
            placeCooldown = placeDelay.get();
            acted = true;
        }
        if (!acted && rotating && now % 10 == 0)
            stopRotating();
    }

    private boolean breakBest(LocalPlayer player, ClientLevel level) {
        Vec3 eye = player.getEyePosition();
        double maxDistance = breakRange.get() * breakRange.get();
        EndCrystal best = null;
        float bestDamage = 0f;
        for (Entity entity : level.entitiesForRendering()) {
            if (!(entity instanceof EndCrystal crystal) || !crystal.isAlive())
                continue;
            if (crystal.getBoundingBox().distanceToSqr(eye) > maxDistance)
                continue;
            if (attacked.containsKey(crystal.getId()) && player.tickCount - attacked.get(crystal.getId()) < ATTACK_RETRY_TICKS)
                continue;

            Vec3 center = crystal.position();
            float damage = ExplosionDamage.crystal(target, center);
            if (damage <= bestDamage || !worthIt(damage) || !safe(player, ExplosionDamage.crystal(player, center)))
                continue;

            best = crystal;
            bestDamage = damage;
        }

        if (best == null)
            return false;

        if (rotations.get()) {
            Vec3 center = best.getBoundingBox().getCenter();
            Client.rotationManager.facePosition(center.x, center.y, center.z, 180f);
            rotating = true;
        }
        mc.gameMode.attack(player, best);
        PlayerUtil.INSTANCE.swingHand(InteractionHand.MAIN_HAND);
        attacked.put(best.getId(), player.tickCount);
        lastDamage = bestDamage;
        return true;
    }

    private boolean placeBest(LocalPlayer player, ClientLevel level) {
        InteractionHand hand = crystalHand(player);
        int switchSlot = -1;
        if (hand == null) {
            if (!autoSwitch.get())
                return false;
            switchSlot = findCrystalSlot(player.getInventory());
            if (switchSlot == -1)
                return false;
            hand = InteractionHand.MAIN_HAND;
        }

        Vec3 eye = player.getEyePosition();
        double maxDistance = placeRange.get() * placeRange.get();
        int radius = Mth.ceil(placeRange.get());
        BlockPos center = BlockPos.containing(eye);
        BlockPos.MutableBlockPos cursor = new BlockPos.MutableBlockPos();
        BlockPos bestBase = null;
        float bestDamage = 0f;
        for (int dx = -radius; dx <= radius; dx++) {
            for (int dy = -radius; dy <= radius; dy++) {
                for (int dz = -radius; dz <= radius; dz++) {
                    cursor.setWithOffset(center, dx, dy, dz);
                    BlockState state = level.getBlockState(cursor);
                    if (!state.is(Blocks.OBSIDIAN) && !state.is(Blocks.BEDROCK))
                        continue;
                    if (eye.distanceToSqr(cursor.getX() + 0.5D, cursor.getY() + 1.0D, cursor.getZ() + 0.5D) > maxDistance)
                        continue;

                    BlockPos above = cursor.above();
                    if (!level.isEmptyBlock(above))
                        continue;
                    AABB space = new AABB(above.getX(), above.getY(), above.getZ(), above.getX() + 1.0D, above.getY() + 2.0D, above.getZ() + 1.0D);
                    if (!level.getEntities((Entity) null, space).isEmpty())
                        continue;

                    Vec3 explosion = new Vec3(above.getX() + 0.5D, above.getY(), above.getZ() + 0.5D);
                    float damage = ExplosionDamage.crystal(target, explosion);
                    if (damage <= bestDamage || !worthIt(damage) || !safe(player, ExplosionDamage.crystal(player, explosion)))
                        continue;

                    bestBase = cursor.immutable();
                    bestDamage = damage;
                }
            }
        }

        if (bestBase == null) {
            lastPlaced = null;
            return false;
        }

        if (switchSlot != -1)
            player.getInventory().setSelectedSlot(switchSlot);
        if (rotations.get()) {
            Client.rotationManager.facePosition(bestBase.getX() + 0.5D, bestBase.getY() + 1.0D, bestBase.getZ() + 0.5D, 180f);
            rotating = true;
        }

        BlockHitResult hit = new BlockHitResult(new Vec3(bestBase.getX() + 0.5D, bestBase.getY() + 1.0D, bestBase.getZ() + 0.5D), Direction.UP, bestBase, false);
        if (mc.gameMode.useItemOn(player, hand, hit).consumesAction())
            PlayerUtil.INSTANCE.swingHand(hand);
        lastPlaced = bestBase.above();
        lastDamage = Math.max(lastDamage, bestDamage);
        return true;
    }

    private boolean worthIt(float damage) {
        if (damage >= minDamage.get())
            return true;
        return damage >= target.getHealth() + target.getAbsorptionAmount();
    }

    private boolean safe(LocalPlayer player, float selfDamage) {
        if (selfDamage > maxSelfDamage.get())
            return false;
        return !antiSuicide.get() || selfDamage < player.getHealth() + player.getAbsorptionAmount() - LETHAL_MARGIN;
    }

    private static InteractionHand crystalHand(LocalPlayer player) {
        if (player.getOffhandItem().is(Items.END_CRYSTAL))
            return InteractionHand.OFF_HAND;
        if (player.getMainHandItem().is(Items.END_CRYSTAL))
            return InteractionHand.MAIN_HAND;
        return null;
    }

    private static int findCrystalSlot(Inventory inventory) {
        for (int slot = 0; slot < Inventory.getSelectionSize(); slot++) {
            if (inventory.getItem(slot).is(Items.END_CRYSTAL))
                return slot;
        }
        return -1;
    }

    @Override
    public void onRender3D(Render3DEvent event) {
        if (lastPlaced == null || target == null)
            highlight.release();
        else
            highlight.setTarget(lastPlaced);
    }
}
