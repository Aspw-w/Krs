package com.instrumentalist.krs.hacks.features.combat;

import com.instrumentalist.krs.events.features.AttackEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.hacks.ModuleManager;
import com.instrumentalist.krs.hacks.features.movement.fly.FlyModule;
import com.instrumentalist.krs.utils.ChatUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.item.MaceItem;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class Criticals extends Module {
    @Setting
    private final ListValue mode = new ListValue("Mode", new String[]{"Packet", "Less Packet"}, "Packet");

    @Setting
    private final BooleanValue cooldownCheck = new BooleanValue("Cooldown Check", false);

    private final double[] packetValues = new double[]{0.0625D, 0.0D, 0.05D, 0.0D};
    private final double[] lessPacketValues = new double[]{0.0625D, 0.0D};

    public Criticals() {
        super("Criticals", ModuleCategory.Combat, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return mode.get();
    }

    @Override
    public void onDisable() {
    }

    @Override
    public void onEnable() {
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.packet;

    }

    @Override
    public void onAttack(AttackEvent event) {
        var player = mc.player;
        if (player == null) return;
        if (!player.onGround()
                || player.getMainHandItem().getItem() instanceof MaceItem
                || ModuleManager.getModuleState(FlyModule.class)
                || (ModuleManager.getModuleState(KillAura.class)
                && KillAura.closestEntity != null
                && (KillAura.randomTp.get()
                || (KillAura.tpReach.get()
                && mc.player.distanceTo(KillAura.closestEntity) >= KillAura.square(ModuleManager.getModule(KillAura.class).attackRange.get())))))
            return;
        if (cooldownCheck.get() && player.getAttackStrengthScale(0f) < 0.8) return;

        Vec3 pos = player.position();
        boolean horizontalCollision = player.horizontalCollision;

        switch (mode.get().toLowerCase()) {
            case "packet":
                for (double critY : packetValues) {
                    PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + critY, pos.z, false, horizontalCollision));
                }
                break;

            case "less packet":
                for (double critY : lessPacketValues) {
                    PacketUtil.sendPacket(new ServerboundMovePlayerPacket.Pos(pos.x, pos.y + critY, pos.z, false, horizontalCollision));
                }
                break;
        }
    }
}
