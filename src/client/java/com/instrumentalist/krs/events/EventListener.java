package com.instrumentalist.krs.events;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.utils.IMinecraft;

public interface EventListener extends IMinecraft {
    default void onKey(KeyboardEvent event) {
    }

    default void onUpdate(UpdateEvent event) {
    }

    default void onSendPacket(SendPacketEvent event) {
    }

    default void onReceivedPacket(ReceivedPacketEvent event) {
    }

    default void onAttack(AttackEvent event) {
    }

    default void onRenderHud(RenderHudEvent event) {
    }

    default void onHandleInput(HandleInputEvent event) {
    }

    default void onMotion(MotionEvent event) {
    }

    default void onWorld(WorldEvent event) {
    }

    default void onBlockEdge(BlockEdgeEvent event) {
    }

    default void onTick(TickEvent event) {
    }

    default void onRender(RenderEvent event) {
    }

    default void onMouseScroll(MouseScrollEvent event) {
    }

    default void onMouseClick(MouseClickEvent event) {
    }

    default void onModifyPacket(ModifyPacketEvent event) {
    }

    default void onFrameBuffer(FrameBufferEvent event) {
    }

    default void onRender3D(Render3DEvent event) {
    }

    default void onBlock(BlockEvent event) {
    }

    default void onCancelBlockBreaking(CancelBlockBreakingEvent event) {
    }
}
