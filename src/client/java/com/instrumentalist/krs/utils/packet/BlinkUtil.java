package com.instrumentalist.krs.utils.packet;

import com.instrumentalist.krs.utils.move.MovementUtil;
import net.minecraft.client.Minecraft;
import net.minecraft.network.protocol.Packet;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayDeque;

public final class BlinkUtil {
    public static final BlinkUtil INSTANCE = new BlinkUtil();
    private static final int MAX_BUFFERED_PACKETS = 2048;

    public final Minecraft mc = Minecraft.getInstance();
    private final ArrayDeque<Packet<?>> packets = new ArrayDeque<>();
    private final ArrayDeque<Packet<?>> incomingPackets = new ArrayDeque<>();
    private final Object flushLock = new Object();
    private Vec3 resetPosition;
    private Double prevYMotion = null;
    private boolean started = false;
    private volatile boolean limiter = false;
    private volatile boolean blinking = false;

    private BlinkUtil() {
    }

    public int getPacketCount() {
        synchronized (packets) {
            return packets.size() + incomingPackets.size();
        }
    }

    public boolean getLimiter() {
        return limiter;
    }

    public void setLimiter(boolean limiter) {
        this.limiter = limiter;
    }

    public boolean getBlinking() {
        return blinking;
    }

    public void setBlinking(boolean blinking) {
        this.blinking = blinking;
    }

    public void addPacket(Packet<?> packet) {
        if (packet == null)
            return;

        synchronized (flushLock) {
            ArrayDeque<Packet<?>> overflowPackets = null;
            synchronized (packets) {
                packets.addLast(packet);
                if (packets.size() >= MAX_BUFFERED_PACKETS)
                    overflowPackets = drainPackets();
            }

            if (overflowPackets != null)
                flushPackets(overflowPackets);
        }
    }

    public void addIncomingPacket(Packet<?> packet) {
        if (packet == null)
            return;

        synchronized (flushLock) {
            ArrayDeque<Packet<?>> overflowPackets = null;
            synchronized (incomingPackets) {
                incomingPackets.addLast(packet);
                if (incomingPackets.size() >= MAX_BUFFERED_PACKETS)
                    overflowPackets = drainIncomingPackets();
            }

            if (overflowPackets != null)
                flushIncomingPackets(overflowPackets);
        }
    }

    public void doBlink() {
        var player = mc.player;
        if (player == null) return;

        blinking = true;
        if (prevYMotion == null)
            prevYMotion = player.getDeltaMovement().y;

        if (!started) {
            var box = player.getBoundingBox();
            resetPosition = new Vec3(player.getX(), box.minY, player.getZ());
            started = true;
            return;
        }

    }

    public void sync(boolean blinkSync) {
        sync(blinkSync, true);
    }

    public void sync(boolean blinkSync, boolean noSyncResetPos) {
        if (blinkSync) {
            synchronized (flushLock) {
                ArrayDeque<Packet<?>> pendingPackets;
                ArrayDeque<Packet<?>> pendingIncomingPackets;
                synchronized (packets) {
                    pendingPackets = drainPackets();
                }
                synchronized (incomingPackets) {
                    pendingIncomingPackets = drainIncomingPackets();
                }
                flushPackets(pendingPackets);
                flushIncomingPackets(pendingIncomingPackets);
            }
            resetPosition = null;
        } else {
            synchronized (flushLock) {
                boolean previousLimiter = limiter;
                try {
                    limiter = true;
                    synchronized (packets) {
                        packets.clear();
                    }
                    synchronized (incomingPackets) {
                        incomingPackets.clear();
                    }
                } finally {
                    limiter = previousLimiter;
                }
            }
            if (noSyncResetPos) {
                var player = mc.player;
                if (resetPosition != null && player != null)
                    player.setPos(resetPosition);
                if (prevYMotion != null)
                    MovementUtil.setVelocityY(prevYMotion);
            }
        }
    }

    public void stopBlink() {
        synchronized (flushLock) {
            synchronized (packets) {
                packets.clear();
            }
            synchronized (incomingPackets) {
                incomingPackets.clear();
            }
        }
        resetPosition = null;
        prevYMotion = null;
        started = false;
        blinking = false;
    }

    private ArrayDeque<Packet<?>> drainPackets() {
        ArrayDeque<Packet<?>> drained = new ArrayDeque<>(packets);
        packets.clear();
        return drained;
    }

    private ArrayDeque<Packet<?>> drainIncomingPackets() {
        ArrayDeque<Packet<?>> drained = new ArrayDeque<>(incomingPackets);
        incomingPackets.clear();
        return drained;
    }

    private void flushPackets(ArrayDeque<Packet<?>> pendingPackets) {
        if (pendingPackets.isEmpty())
            return;

        boolean previousLimiter = limiter;
        try {
            limiter = true;
            while (!pendingPackets.isEmpty()) {
                Packet<?> packet = pendingPackets.removeFirst();
                try {
                    PacketUtil.sendPacket(packet);
                } catch (RuntimeException failure) {
                    pendingPackets.addFirst(packet);
                    while (pendingPackets.size() > MAX_BUFFERED_PACKETS)
                        pendingPackets.removeLast();
                    synchronized (packets) {
                        packets.addAll(pendingPackets);
                    }
                    return;
                }
            }
        } finally {
            limiter = previousLimiter;
        }
    }

    private void flushIncomingPackets(ArrayDeque<Packet<?>> pendingPackets) {
        if (pendingPackets.isEmpty())
            return;

        while (!pendingPackets.isEmpty()) {
            Packet<?> packet = pendingPackets.removeFirst();
            try {
                PacketUtil.handlePacket(packet);
            } catch (RuntimeException failure) {
                pendingPackets.addFirst(packet);
                while (pendingPackets.size() > MAX_BUFFERED_PACKETS)
                    pendingPackets.removeLast();
                synchronized (incomingPackets) {
                    incomingPackets.addAll(pendingPackets);
                }
                return;
            }
        }
    }
}
