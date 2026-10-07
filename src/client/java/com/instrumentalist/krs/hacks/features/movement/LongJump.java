package com.instrumentalist.krs.hacks.features.movement;

import com.instrumentalist.krs.events.features.MotionEvent;
import com.instrumentalist.krs.events.features.ReceivedPacketEvent;
import com.instrumentalist.krs.events.features.SendPacketEvent;
import com.instrumentalist.krs.events.features.UpdateEvent;
import com.instrumentalist.krs.hacks.Module;
import com.instrumentalist.krs.hacks.ModuleCategory;
import com.instrumentalist.krs.utils.math.TimerUtil;
import com.instrumentalist.krs.utils.move.MovementUtil;
import com.instrumentalist.krs.utils.packet.PacketUtil;
import com.instrumentalist.krs.utils.value.BooleanValue;
import com.instrumentalist.krs.utils.value.ListValue;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientboundPlayerPositionPacket;
import net.minecraft.network.protocol.game.ClientboundSetEntityMotionPacket;
import net.minecraft.network.protocol.game.ServerboundAcceptTeleportationPacket;
import net.minecraft.network.protocol.game.ServerboundMovePlayerPacket;
import net.minecraft.world.entity.PositionMoveRotation;
import net.minecraft.world.phys.Vec3;
import org.lwjgl.glfw.GLFW;

public class LongJump extends Module {

    @Setting
    private final ListValue longJumpMode = new ListValue(
            "Long Jump Mode",
            new String[]{"Matrix", "Grim"},
            "Matrix"
    );

    @Setting
    public static final BooleanValue airViewBobbing = new BooleanValue("Air View Bobbing", true);

    private boolean start;
    private Vec3 matrix2StartCameraPosition = Vec3.ZERO;
    private boolean matrix2Started;
    private boolean matrix2Airborne;
    private int grimTicks;

    public LongJump() {
        super("Long Jump", ModuleCategory.Movement, GLFW.GLFW_KEY_UNKNOWN, false, true);
    }

    @Override
    public String tag() {
        return longJumpMode.get();
    }

    @Override
    public void onEnable() {
        if (longJumpMode.get().equalsIgnoreCase("grim")) {
            grimTicks = 0;

            if (mc.player != null && MovementUtil.fallTicks >= 2) {
                for (int i = 0; i < 20; i++)
                    PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.StatusOnly(false, mc.player.horizontalCollision));
            }
        }
    }

    @Override
    public void onDisable() {
        matrix2Started = false;
        matrix2Airborne = false;
        start = false;
        matrix2StartCameraPosition = Vec3.ZERO;
        grimTicks = 0;
        TimerUtil.reset();
    }

    @Override
    public void onUpdate(UpdateEvent event) {
        if (mc.player == null) {
            matrix2Started = false;
            matrix2Airborne = false;
            start = false;
            matrix2StartCameraPosition = Vec3.ZERO;
            grimTicks = 0;
            return;
        }

        switch (longJumpMode.get().toLowerCase()) {
            case "grim":
                grimTicks++;

                if (mc.player.onGround())
                    mc.player.input.makeJump();

                int airTicks = MovementUtil.fallTicks;
                if (airTicks == 1) {
                    PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.StatusOnly(true, mc.player.horizontalCollision));
                    PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.StatusOnly(false, mc.player.horizontalCollision));
                } else if (airTicks == 2) {
                    for (int i = 0; i < 10; i++)
                        PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.StatusOnly(false, mc.player.horizontalCollision));
                } else if (grimTicks == 2) {
                    for (int i = 0; i < 4; i++)
                        PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.StatusOnly(false, mc.player.horizontalCollision));
                }
                break;

            case "matrix":
                if (!mc.player.isSprinting()) {
                    matrix2Started = false;
                    matrix2Airborne = false;
                    start = false;
                    matrix2StartCameraPosition = Vec3.ZERO;
                    return;
                }

                if (!start) {
                    matrix2Started = false;
                    matrix2Airborne = false;

                    if (mc.player != null)
                        matrix2StartCameraPosition = new Vec3(mc.player.getX(), mc.player.getEyeY(), mc.player.getZ());
                    else
                        matrix2StartCameraPosition = Vec3.ZERO;

                    start = true;
                    return;
                }

                if (matrix2Airborne && mc.player.onGround()) {
                    setState(false);
                    return;
                }

                if (mc.player.onGround())
                    mc.player.input.makeJump();

                matrix2Started = true;

                if (mc.player.tickCount > 1)
                    MovementUtil.setVelocityY(mc.player.getDeltaMovement().y + 0.00348);

                if (matrix2Started && mc.player.fallDistance > 0.1F) {
                    MovementUtil.setVelocityY(0.42);
                    MovementUtil.strafe(1.97F);
                }

                matrix2Airborne = !mc.player.onGround();
                break;
        }
    }

    @Override
    public void onMotion(MotionEvent event) {
        switch (longJumpMode.get().toLowerCase()) {
            case "grim":
                event.pitch += (float) (Math.random() * 0.5);

                int airTicks = MovementUtil.fallTicks;
                if (grimTicks > 4)
                    TimerUtil.timerSpeed = 1.0F;
                else if (grimTicks > 2)
                    TimerUtil.timerSpeed = 1.0F;
                else if (airTicks == 1)
                    TimerUtil.timerSpeed = 0.8F;
                else if (airTicks > 2)
                    TimerUtil.timerSpeed = 2.0F;
                break;

            case "matrix":
                if (matrix2Started && mc.player != null && mc.player.fallDistance > 0.1F)
                    event.isMoving = true;
                break;
        }
    }

    @Override
    public void onSendPacket(SendPacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.packet;

        if (longJumpMode.get().equalsIgnoreCase("grim") && packet instanceof ServerboundMovePlayerPacket && grimTicks >= 2 && MovementUtil.fallTicks >= 1)
            event.cancel();
    }

    @Override
    public void onReceivedPacket(ReceivedPacketEvent event) {
        if (mc.player == null) return;

        Packet<?> packet = event.packet;

        switch (longJumpMode.get().toLowerCase()) {
            case "grim":
                if (packet instanceof ClientboundSetEntityMotionPacket(int id1, Vec3 movement) && id1 == mc.player.getId()) {
                    if (movement.y / 8000.0 < 0.0)
                        setState(false);
                }
                break;

            case "matrix":
                if (!(packet instanceof ClientboundPlayerPositionPacket(
                        int id, PositionMoveRotation change, java.util.Set<net.minecraft.world.entity.Relative> relatives
                )))
                    return;

                event.cancel();

                PositionMoveRotation absolute = PositionMoveRotation.calculateAbsolute(
                        PositionMoveRotation.of(mc.player),
                        change,
                        relatives
                );
                Vec3 position = absolute.position();

                PacketUtil.sendPacketAsSilent(new ServerboundAcceptTeleportationPacket(id));
                PacketUtil.sendPacketAsSilent(new ServerboundMovePlayerPacket.PosRot(
                        position.x,
                        position.y,
                        position.z,
                        mc.player.getYRot(),
                        mc.player.getXRot(),
                        false,
                        mc.player.horizontalCollision
                ));

                mc.player.setPos(position);
                mc.player.jumpFromGround();
                setState(false);
                break;
        }
    }

    public boolean shouldUseMatrix2SilentCamera() {
        return tempEnabled && matrix2StartCameraPosition != Vec3.ZERO;
    }

    public Vec3 getMatrix2SilentCameraPosition() {
        return matrix2StartCameraPosition;
    }
}
