package com.instrumentalist.krs.hacks.features.player;

import com.instrumentalist.krs.events.features.MouseClickEvent;
import com.instrumentalist.krs.events.features.MouseScrollEvent;
import com.instrumentalist.krs.events.features.TickEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.events.features.WorldEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.utils.input.Keys;
import com.instrumentalist.krs.utils.move.InputUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.IntValue;
import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.ChatFormatting;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.player.ClientInput;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.player.Input;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.ClipContext;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.BlockHitResult;
import net.minecraft.world.phys.HitResult;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.CollisionContext;
import net.minecraft.world.phys.shapes.VoxelShape;

import java.util.List;
import com.mojang.authlib.GameProfile;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.world.item.component.ResolvableProfile;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.Optional;

public class Freecam extends Module {

    private static final double EYE_HEIGHT = 1.62;
    private static final int SLOT_SPEED = 0;
    private static final int SLOT_WAND = 1;
    private static final int SLOT_NO_CLIP = 2;
    private static final int SLOT_PLAYERS = 3;
    private static final int PLAYERS_PER_PAGE = 7;
    private static final int SLOT_NEXT_PAGE = 7;
    private static final int SLOT_CONTROL = 4;
    private static final int SLOT_BACK = 8;

    private static final IntValue speed = new IntValue("Speed", 2, 1, 9);

    private static final BooleanValue noClip = new BooleanValue("No Clip", false);

    private static boolean canFly = false;
    private static Vec3 camPos = Vec3.ZERO;
    private static Vec3 prevCamPos = Vec3.ZERO;
    private static float camYaw = 0f;
    private static float camPitch = 0f;

    private static boolean changingSpeed = false;
    private static boolean listingPlayers = false;
    private static int playerSlot = 0;
    private static int playerPage = 0;
    private static final ItemStack[] playerItems = new ItemStack[9];
    private static final UUID[] playerSlots = new UUID[9];
    private static final Map<UUID, ItemStack> headCache = new HashMap<>();
    private static ItemStack nextPageItem = ItemStack.EMPTY;
    private static String nextPageLabel = "";
    private static int selectedTool = 0;
    private static final ItemStack[] hotbar = new ItemStack[9];
    private static final ItemStack[] speedItems = new ItemStack[9];
    private static boolean rightHeld = false;
    private static boolean leftHeld = false;
    private static boolean keepSneak = false;

    private Vec3 flyMotion = Vec3.ZERO;
    private final Simulation simulation = new Simulation();

    public Freecam() {
        super("Freecam", ModuleCategory.Player, Keys.KEY_UNKNOWN, false, true);
    }

    @Override
    public void onEnable() {
        var player = mc.player;
        if (player == null) return;

        mc.options.keyUse.setDown(false);
        mc.options.keyAttack.setDown(false);
        if (player.isUsingItem() && mc.gameMode != null)
            mc.gameMode.releaseUsingItem(player);
        if (mc.gameMode != null)
            mc.gameMode.stopDestroyBlock();

        camPos = player.position().add(0.0, EYE_HEIGHT, 0.0);
        prevCamPos = camPos;
        camYaw = player.getYRot();
        camPitch = player.getXRot();
        keepSneak = mc.options.keyShift.isDown();
        canFly = true;
        changingSpeed = false;
        resetPlayerList();
        rightHeld = false;
        leftHeld = false;
        flyMotion = Vec3.ZERO;
        simulation.reset(player.position(), Vec3.ZERO, true);
        makeToolBar();
    }

    @Override
    public void onDisable() {
        canFly = false;
        changingSpeed = false;
        resetPlayerList();
        rightHeld = false;
        leftHeld = false;
        keepSneak = false;
        selectedTool = 0;
        speed.set(2);
        noClip.set(false);
        makeNoClipTool();
    }

    @Override
    public void onWorld(WorldEvent event) {
        toggle();
    }

    @Override
    public void onTick(TickEvent event) {
        if (!isHotbarActive())
            return;

        KeyMapping[] slots = mc.options.keyHotbarSlots;
        for (int i = 0; i < slots.length; i++) {
            while (slots[i].consumeClick())
                selectSlot(i);
        }
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        LocalPlayer player = mc.player;
        if (player == null || !canFly) return;

        if (isAimingPlayer())
            aimPlayerAtCameraTarget(player);

        if (listingPlayers)
            refreshPlayerList();

        boolean blocked = mc.gui.screen() != null || isControllingPlayer();
        boolean forward = !blocked && isKeyDown(mc.options.keyUp);
        boolean back = !blocked && isKeyDown(mc.options.keyDown);
        boolean left = !blocked && isKeyDown(mc.options.keyLeft);
        boolean right = !blocked && isKeyDown(mc.options.keyRight);
        boolean jump = !blocked && isKeyDown(mc.options.keyJump);
        boolean sneak = !blocked && isKeyDown(mc.options.keyShift);
        boolean sprint = !blocked && isKeyDown(mc.options.keySprint);

        float forwardInput = (forward ? 1f : 0f) - (back ? 1f : 0f);
        float strafeInput = (left ? 1f : 0f) - (right ? 1f : 0f);

        prevCamPos = camPos;

        if (noClip.get()) {
            double flySpeed = Simulation.flySpeed(speed.get());
            if (sneak) flyMotion = flyMotion.add(0.0, -flySpeed * 3.0, 0.0);
            if (jump) flyMotion = flyMotion.add(0.0, flySpeed * 3.0, 0.0);
            flyMotion = flyMotion.add(Simulation.relative(strafeInput, forwardInput, flySpeed * (sprint ? 2.0 : 1.0), camYaw));
            camPos = camPos.add(flyMotion);
            flyMotion = new Vec3(flyMotion.x * 0.91, flyMotion.y * 0.6, flyMotion.z * 0.91);
            return;
        }

        if (mc.level == null)
            return;

        simulation.tick(mc.level, forwardInput, strafeInput, jump, sneak, sprint, camYaw, speed.get());
        camPos = simulation.position.add(0.0, EYE_HEIGHT, 0.0);
    }

    @Override
    public void onMouseScroll(MouseScrollEvent event) {
        if (!isHotbarActive() || mc.gui.screen() != null || event.vertical == 0.0)
            return;

        int direction = event.vertical > 0 ? 1 : -1;
        scroll(direction);
        event.cancel();
    }

    @Override
    public void onMouseClick(MouseClickEvent event) {
        if (!isHotbarActive())
            return;

        boolean left = event.button == Keys.MOUSE_BUTTON_LEFT;
        boolean right = event.button == Keys.MOUSE_BUTTON_RIGHT;
        if (!left && !right)
            return;

        boolean press = event.action == Keys.PRESS;
        boolean release = event.action == Keys.RELEASE;

        if (release) {
            if (right)
                rightHeld = false;
            else
                leftHeld = false;
            return;
        }

        if (mc.gui.screen() != null)
            return;

        event.cancel();

        if (right) {
            if (press || release)
                rightHeld = press;
            if (press)
                useTool();
        } else {
            if (press || release)
                leftHeld = press;
            if (press)
                attackTool();
        }
    }

    private void scroll(int direction) {
        int slot = Math.floorMod(selectedSlot() - direction, 9);
        if (changingSpeed) {
            speed.set(slot + 1);
            return;
        }
        if (listingPlayers) {
            playerSlot = slot;
            for (int i = 0; i < 9 && getHotbarStack(playerSlot).isEmpty(); i++)
                playerSlot = Math.floorMod(playerSlot - direction, 9);
            return;
        }
        selectedTool = slot;
        if (getHotbarStack(selectedTool).isEmpty()) {
            for (int i = 0; i < 9 && getHotbarStack(selectedTool).isEmpty(); i++)
                selectedTool = Math.floorMod(selectedTool - direction, 9);
        }
    }

    private void selectSlot(int slot) {
        if (changingSpeed)
            speed.set(Mth.clamp(slot + 1, speed.minimum, speed.maximum));
        else if (listingPlayers)
            playerSlot = slot;
        else
            selectedTool = slot;
    }

    private void useTool() {
        if (changingSpeed) {
            changingSpeed = false;
            return;
        }

        if (listingPlayers) {
            usePlayerList();
            return;
        }

        switch (selectedTool) {
            case SLOT_SPEED -> changingSpeed = true;
            case SLOT_PLAYERS -> {
                listingPlayers = true;
                playerSlot = 0;
                playerPage = 0;
                refreshPlayerList();
            }
            case SLOT_WAND -> teleportCamera();
            case SLOT_NO_CLIP -> {
                if (noClip.get())
                    simulation.reset(camPos.subtract(0.0, EYE_HEIGHT, 0.0), flyMotion, true);
                else
                    flyMotion = Vec3.ZERO;
                noClip.set(!noClip.get());
                makeNoClipTool();
            }
            case SLOT_BACK -> {
                if (ModuleManager.getModuleState(Freecam.class))
                    toggle();
            }
            default -> {
            }
        }
    }

    private void attackTool() {
        if (!changingSpeed && !listingPlayers && selectedTool == SLOT_WAND)
            spectateNearestEntity();
    }

    private static int selectedSlot() {
        if (changingSpeed)
            return speed.get() - 1;
        return listingPlayers ? playerSlot : selectedTool;
    }

    private static void resetPlayerList() {
        listingPlayers = false;
        playerSlot = 0;
        playerPage = 0;
        headCache.clear();
        for (int i = 0; i < 9; i++) {
            playerItems[i] = ItemStack.EMPTY;
            playerSlots[i] = null;
        }
    }

    private void usePlayerList() {
        if (playerSlot == SLOT_BACK) {
            listingPlayers = false;
            return;
        }
        if (playerSlot == SLOT_NEXT_PAGE && !playerItems[SLOT_NEXT_PAGE].isEmpty()) {
            playerPage++;
            refreshPlayerList();
            return;
        }
        UUID id = playerSlots[playerSlot];
        if (id == null || mc.level == null)
            return;
        Player target = mc.level.getPlayerByUUID(id);
        if (target != null)
            spectate(target, true);
    }

    private static void refreshPlayerList() {
        if (mc.level == null || mc.player == null)
            return;

        List<Player> players = new ArrayList<>();
        for (Player other : mc.level.players()) {
            if (other != mc.player && other.isAlive() && !other.isSpectator())
                players.add(other);
        }
        players.sort(Comparator.comparing(other -> other.getGameProfile().name(), String.CASE_INSENSITIVE_ORDER));

        int pages = Math.max(1, (players.size() + PLAYERS_PER_PAGE - 1) / PLAYERS_PER_PAGE);
        playerPage = Math.floorMod(playerPage, pages);

        for (int i = 0; i < 9; i++) {
            playerItems[i] = ItemStack.EMPTY;
            playerSlots[i] = null;
        }

        Set<UUID> visible = new HashSet<>();
        int start = playerPage * PLAYERS_PER_PAGE;
        for (int i = 0; i < PLAYERS_PER_PAGE && start + i < players.size(); i++) {
            Player other = players.get(start + i);
            UUID id = other.getUUID();
            visible.add(id);
            playerSlots[i] = id;
            playerItems[i] = headCache.computeIfAbsent(id, key -> makeHead(other));
        }
        headCache.keySet().retainAll(visible);

        if (pages > 1) {
            String label = "Next Page (" + (playerPage + 1) + "/" + pages + ")";
            if (!label.equals(nextPageLabel)) {
                nextPageLabel = label;
                nextPageItem = named(Items.ARROW, Component.literal(label).withStyle(ChatFormatting.YELLOW), 1, null);
            }
            playerItems[SLOT_NEXT_PAGE] = nextPageItem;
        }
        playerItems[SLOT_BACK] = hotbar[SLOT_BACK];
    }

    private static ItemStack makeHead(Player other) {
        GameProfile profile = other.getGameProfile();
        if (mc.getConnection() != null) {
            PlayerInfo info = mc.getConnection().getPlayerInfo(other.getUUID());
            if (info != null)
                profile = info.getProfile();
        }
        ItemStack head = named(Items.PLAYER_HEAD, Component.literal(profile.name()).withStyle(ChatFormatting.WHITE), 1,
                "Right click to go to this player");
        head.set(DataComponents.PROFILE, ResolvableProfile.createResolved(profile));
        return head;
    }

    private static void makeToolBar() {
        for (int i = 0; i < 9; i++) {
            hotbar[i] = ItemStack.EMPTY;
            speedItems[i] = named(Items.SUGAR, Component.literal("Speed ").withStyle(ChatFormatting.AQUA)
                    .append(Component.literal(String.valueOf(i + 1)).withStyle(ChatFormatting.GRAY)), i + 1, null);
        }
        hotbar[SLOT_SPEED] = named(Items.SUGAR, Component.literal("Speed").withStyle(ChatFormatting.AQUA), 1, null);
        hotbar[SLOT_PLAYERS] = named(Items.PLAYER_HEAD, Component.literal("Players").withStyle(ChatFormatting.YELLOW), 1,
                "Right click to list players whose position is known");
        hotbar[SLOT_WAND] = named(Items.BLAZE_ROD, Component.literal("Magic Wand").withStyle(ChatFormatting.DARK_PURPLE), 1, null);
        hotbar[SLOT_CONTROL] = named(Items.LEAD, Component.literal("Player Control").withStyle(ChatFormatting.GOLD), 1,
                "Hold this item to take control of your player");
        hotbar[SLOT_BACK] = named(Items.FEATHER, Component.literal("Back").withStyle(ChatFormatting.GRAY), 1, null);
        makeNoClipTool();
    }

    private static void makeNoClipTool() {
        boolean enabled = noClip.get();
        hotbar[SLOT_NO_CLIP] = named(enabled ? Items.SADDLE : Items.BARRIER,
                Component.literal(enabled ? "Disable No-Clip" : "Enable No-Clip").withStyle(enabled ? ChatFormatting.RED : ChatFormatting.GREEN), 1, null);
    }

    private static ItemStack named(Item item, Component name, int count, String lore) {
        ItemStack stack = new ItemStack(item, count);
        stack.set(DataComponents.CUSTOM_NAME, name.copy().withStyle(style -> style.withItalic(false)));
        if (lore != null)
            stack.set(DataComponents.LORE, new ItemLore(List.of(Component.literal(lore).withStyle(ChatFormatting.GRAY))));
        return stack;
    }

    private void teleportCamera() {
        if (mc.level == null || mc.player == null)
            return;

        Vec3 start = camPos;
        Vec3 end = start.add(getScaledCamDir(300.0));
        BlockHitResult blockHit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.COLLIDER, ClipContext.Fluid.NONE, mc.player));
        if (blockHit.getType() == HitResult.Type.BLOCK) {
            BlockPos pos = blockHit.getBlockPos();
            VoxelShape shape = mc.level.getBlockState(pos).getCollisionShape(mc.level, pos);
            double top = shape.isEmpty() ? 1.0 : shape.max(Direction.Axis.Y);
            moveCamera(new Vec3(pos.getX() + 0.5, pos.getY() + top, pos.getZ() + 0.5));
            return;
        }

        Entity entity = pickEntity(start, end, Double.MAX_VALUE);
        if (entity != null)
            spectate(entity);
    }

    private static Entity pickEntity(Vec3 start, Vec3 end, double maxDistanceSqr) {
        Entity best = null;
        double bestDistance = maxDistanceSqr;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || entity.isSpectator() || !entity.isPickable())
                continue;
            Optional<Vec3> hit = entity.getBoundingBox().inflate(entity.getPickRadius()).clip(start, end);
            if (hit.isEmpty())
                continue;
            double distance = hit.get().distanceToSqr(start);
            if (distance < bestDistance) {
                bestDistance = distance;
                best = entity;
            }
        }
        return best;
    }

    private void spectateNearestEntity() {
        if (mc.level == null)
            return;

        Vec3 feet = camPos.subtract(0.0, EYE_HEIGHT, 0.0);
        Entity nearest = null;
        double best = Double.MAX_VALUE;
        for (Entity entity : mc.level.entitiesForRendering()) {
            if (entity == mc.player || !(entity instanceof Player) || entity.isSpectator() || !entity.isAlive())
                continue;
            double dx = entity.getX() - feet.x;
            double dy = entity.getY() - feet.y;
            double dz = entity.getZ() - feet.z;
            float yaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
            float pitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);
            float yawDistance = Mth.wrapDegrees(camYaw - yaw);
            float pitchDistance = camPitch - pitch;
            double distance = Math.sqrt(yawDistance * yawDistance + pitchDistance * pitchDistance);
            if (distance < best) {
                best = distance;
                nearest = entity;
            }
        }
        if (nearest != null && best < 16.0)
            spectate(nearest);
    }

    private void spectate(Entity entity) {
        spectate(entity, false);
    }

    private void spectate(Entity entity, boolean force) {
        Level level = mc.level;
        for (double x = -0.5; x <= 0.5; x += 0.5) {
            for (double y = 0.0; y < 2.5; y += 0.5) {
                for (double z = -0.5; z <= 0.5; z += 0.5) {
                    level.addParticle(ParticleTypes.PORTAL, true, true, entity.getX(), entity.getY(), entity.getZ(),
                            x + Math.random() * 0.1, y + Math.random() * 0.1, z + Math.random() * 0.1);
                    level.addParticle(ParticleTypes.PORTAL, true, true, entity.getX(), entity.getY(), entity.getZ(),
                            x + Math.random() * 0.1, y + Math.random() * 0.1, z + Math.random() * 0.1);
                }
            }
        }

        Vec3 feet = camPos.subtract(0.0, EYE_HEIGHT, 0.0);
        if (!force && feet.distanceToSqr(entity.position()) <= 64.0)
            return;

        double yawRad = Math.toRadians(entity.getYRot() - 180f);
        Vec3 position = new Vec3(
                entity.getX() - Math.sin(yawRad) * 3.5,
                entity.getY() + 1.35,
                entity.getZ() + Math.cos(yawRad) * 3.5
        );
        moveCamera(position);

        double dx = entity.getX() - position.x;
        double dy = entity.getY() - position.y;
        double dz = entity.getZ() - position.z;
        camYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        camPitch = Mth.clamp((float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG), -90f, 90f);
    }

    private void moveCamera(Vec3 feet) {
        camPos = feet.add(0.0, EYE_HEIGHT, 0.0);
        prevCamPos = camPos;
        flyMotion = Vec3.ZERO;
        simulation.reset(feet, Vec3.ZERO, simulation.flying);
    }

    private static void aimPlayerAtCameraTarget(LocalPlayer player) {
        if (mc.level == null)
            return;

        Vec3 start = camPos;
        Vec3 end = start.add(getScaledCamDir(85.0));
        BlockHitResult blockHit = mc.level.clip(new ClipContext(start, end, ClipContext.Block.OUTLINE, ClipContext.Fluid.NONE, player));
        double blockDistance = blockHit.getType() == HitResult.Type.MISS ? Double.MAX_VALUE : blockHit.getLocation().distanceToSqr(start);
        Entity entity = pickEntity(start, end, blockDistance);

        Vec3 target;
        if (entity != null)
            target = entity.getBoundingBox().getCenter();
        else if (blockHit.getType() != HitResult.Type.MISS)
            target = blockHit.getLocation();
        else
            target = end;

        Vec3 eyes = player.getEyePosition();
        double dx = target.x - eyes.x;
        double dy = target.y - eyes.y;
        double dz = target.z - eyes.z;
        float targetYaw = (float) (Mth.atan2(dz, dx) * Mth.RAD_TO_DEG) - 90f;
        float targetPitch = (float) -(Mth.atan2(dy, Math.sqrt(dx * dx + dz * dz)) * Mth.RAD_TO_DEG);

        double sensitivity = mc.options.sensitivity().get() * 0.6 + 0.2;
        double step = sensitivity * sensitivity * sensitivity * 8.0 * 0.15;
        float yaw = player.getYRot();
        float pitch = player.getXRot();
        float yawDelta = (float) (Math.round(Mth.wrapDegrees(targetYaw - yaw) / step) * step);
        float pitchDelta = (float) (Math.round((targetPitch - pitch) / step) * step);
        player.setYRot(yaw + yawDelta);
        player.setXRot(Mth.clamp(pitch + pitchDelta, -90f, 90f));
    }

    private boolean isKeyDown(KeyMapping key) {
        return InputConstants.isKeyDown(InputConstants.getKey(key.saveString()).getValue());
    }

    public static boolean isHotbarActive() {
        return canFly && ModuleManager.getModuleState(Freecam.class) && mc.player != null;
    }

    public static int getHotbarSelectedSlot() {
        return selectedSlot();
    }

    public static ItemStack getHotbarStack(int slot) {
        if (slot < 0 || slot >= 9)
            return ItemStack.EMPTY;
        ItemStack stack = changingSpeed ? speedItems[slot] : listingPlayers ? playerItems[slot] : hotbar[slot];
        return stack == null ? ItemStack.EMPTY : stack;
    }

    public static boolean isAimingPlayer() {
        return canFly && !changingSpeed && !listingPlayers && selectedTool == SLOT_CONTROL && leftHeld && mc.gui.screen() == null;
    }

    public static boolean isControllingPlayer() {
        return canFly && !changingSpeed && !listingPlayers && selectedTool == SLOT_CONTROL && rightHeld;
    }

    public static boolean blocksPlayerInput() {
        return ModuleManager.getModuleState(Freecam.class) && canFly && !isControllingPlayer();
    }

    public static boolean keepsSneak() {
        return keepSneak && blocksPlayerInput();
    }

    public static void applyKeptInput(ClientInput input) {
        if (input == null || !keepsSneak())
            return;
        Input keys = input.keyPresses == null ? Input.EMPTY : input.keyPresses;
        boolean jump = keys.jump() || mc.player != null && mc.player.getAbilities().flying;
        InputUtil.applyInput(input, new Input(keys.forward(), keys.backward(), keys.left(), keys.right(), jump, true, keys.sprint()));
    }

    public static boolean getCanFly() {
        return canFly;
    }

    public static void setCanFly(boolean value) {
        canFly = value;
    }

    public static Vec3 getCamPos(float partialTicks) {
        return new Vec3(
                Mth.lerp((double) partialTicks, prevCamPos.x, camPos.x),
                Mth.lerp((double) partialTicks, prevCamPos.y, camPos.y),
                Mth.lerp((double) partialTicks, prevCamPos.z, camPos.z)
        );
    }

    public static float getCamYaw() {
        return camYaw;
    }

    public static float getCamPitch() {
        return camPitch;
    }

    public static void turn(double deltaYaw, double deltaPitch) {
        if (isAimingPlayer()) {
            if (mc.player != null)
                aimPlayerAtCameraTarget(mc.player);
            return;
        }
        camYaw += (float) (deltaYaw * 0.15);
        camPitch += (float) (deltaPitch * 0.15);
        camPitch = Mth.clamp(camPitch, -90f, 90f);
    }

    public static Vec3 getScaledCamDir(double scale) {
        return Vec3.directionFromRotation(camPitch, camYaw).scale(scale);
    }

    private static final class Simulation {
        private Vec3 position = Vec3.ZERO;
        private Vec3 motion = Vec3.ZERO;
        private boolean onGround;
        private boolean flying;
        private boolean lastJump;
        private int flyToggleTimer;

        static double movementSpeed(int speedLevel) {
            return speedLevel * 0.14 - (speedLevel >= 4 ? speedLevel * 0.05 : 0.0);
        }

        static double flySpeed(int speedLevel) {
            return movementSpeed(speedLevel) / 2.54;
        }

        static Vec3 relative(float strafe, float forward, double accel, float yaw) {
            double inputLength = strafe * strafe + forward * forward;
            if (inputLength < 1.0E-4)
                return Vec3.ZERO;
            double scale = accel / Math.max(1.0, Math.sqrt(inputLength));
            double s = strafe * scale;
            double f = forward * scale;
            double rad = Math.toRadians(yaw);
            double sin = Math.sin(rad);
            double cos = Math.cos(rad);
            return new Vec3(s * cos - f * sin, 0.0, f * cos + s * sin);
        }

        void reset(Vec3 feet, Vec3 velocity, boolean fly) {
            position = feet;
            motion = velocity;
            flying = fly;
            onGround = false;
            lastJump = false;
            flyToggleTimer = 0;
        }

        void tick(Level level, float forward, float strafe, boolean jump, boolean sneak, boolean sprintKey, float yaw, int speedLevel) {
            double movementSpeed = movementSpeed(speedLevel);
            double flySpeed = flySpeed(speedLevel);

            if (flyToggleTimer > 0)
                flyToggleTimer--;
            if (!lastJump && jump) {
                if (flyToggleTimer > 0) {
                    flying = !flying;
                    flyToggleTimer = 0;
                } else {
                    flyToggleTimer = 7;
                }
            }
            lastJump = jump;

            if (!flying && sneak) {
                strafe *= 0.3f;
                forward *= 0.3f;
            }
            boolean sprinting = flying ? sprintKey : forward != 0f || strafe != 0f;

            if (flying) {
                if (onGround)
                    flying = false;
                double vertical = flySpeed * 3.0;
                if (sneak) motion = motion.add(0.0, -vertical, 0.0);
                if (jump) motion = motion.add(0.0, vertical, 0.0);
            } else if (jump && onGround) {
                motion = new Vec3(motion.x, 0.42, motion.z);
                if (sprinting) {
                    double rad = Math.toRadians(yaw);
                    motion = motion.add(-Math.sin(rad) * 0.2, 0.0, Math.cos(rad) * 0.2);
                }
            }

            double groundFriction = onGround ? blockFriction(level) * 0.91 : 0.91;
            double accel;
            if (flying)
                accel = flySpeed * (sprinting ? 2.0 : 1.0);
            else if (onGround)
                accel = movementSpeed * (sprinting ? 1.3 : 1.0) * (0.21600002 / (groundFriction * groundFriction * groundFriction));
            else
                accel = sprinting ? 0.025999999 : 0.02;

            motion = motion.add(relative(strafe, forward, accel, yaw));

            Vec3 moved = move(level, motion);
            boolean collidedX = !Mth.equal(motion.x, moved.x);
            boolean collidedZ = !Mth.equal(motion.z, moved.z);
            onGround = motion.y < 0.0 && !Mth.equal(motion.y, moved.y);
            position = position.add(moved);

            double friction = onGround ? blockFriction(level) * 0.91 : 0.91;
            double motionY = moved.y;
            if (!Mth.equal(motion.y, moved.y))
                motionY = 0.0;
            motionY = flying ? motionY * 0.6 : (motionY - 0.08) * 0.98;
            motion = new Vec3(collidedX ? 0.0 : moved.x * friction, motionY, collidedZ ? 0.0 : moved.z * friction);
        }

        private Vec3 move(Level level, Vec3 movement) {
            AABB box = box(position);
            Vec3 collided = Entity.collideBoundingBox(CollisionContext.empty(), movement, box, level, List.of());
            boolean horizontalBlocked = !Mth.equal(movement.x, collided.x) || !Mth.equal(movement.z, collided.z);
            boolean grounded = onGround || movement.y < 0.0 && !Mth.equal(movement.y, collided.y);
            if (!horizontalBlocked || !grounded)
                return collided;

            Vec3 up = Entity.collideBoundingBox(CollisionContext.empty(), new Vec3(0.0, 0.6, 0.0), box, level, List.of());
            AABB raised = box.move(up);
            Vec3 across = Entity.collideBoundingBox(CollisionContext.empty(), new Vec3(movement.x, 0.0, movement.z), raised, level, List.of());
            AABB moved = raised.move(across);
            Vec3 down = Entity.collideBoundingBox(CollisionContext.empty(), new Vec3(0.0, -up.y + Math.min(0.0, movement.y), 0.0), moved, level, List.of());
            Vec3 stepped = up.add(across).add(down);
            if (stepped.horizontalDistanceSqr() > collided.horizontalDistanceSqr())
                return stepped;
            return collided;
        }

        private double blockFriction(Level level) {
            return level.getBlockState(BlockPos.containing(position.x, position.y - 0.5000001, position.z)).getBlock().getFriction();
        }

        private static AABB box(Vec3 feet) {
            return new AABB(feet.x - 0.3, feet.y, feet.z - 0.3, feet.x + 0.3, feet.y + 1.8, feet.z + 0.3);
        }
    }
}
