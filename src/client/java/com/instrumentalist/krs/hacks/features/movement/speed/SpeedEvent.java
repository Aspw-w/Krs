package com.instrumentalist.krs.hacks.features.movement.speed;

import com.instrumentalist.krs.events.features.*;
import com.instrumentalist.krs.utils.IMinecraft;

public interface SpeedEvent extends IMinecraft {
    String getName();
    void onUpdate(UpdateEvent event);
    void onMotion(MotionEvent event);
    void onMove(MoveEvent event);
    void onTick(TickEvent event);
    void onSendPacket(SendPacketEvent event);
    void onReceivedPacket(ReceivedPacketEvent event);
}
