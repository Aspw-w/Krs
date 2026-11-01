package com.instrumentalist.krs.events.features;

import com.instrumentalist.krs.events.EventArgument;
import com.instrumentalist.krs.events.EventListener;
import net.minecraft.client.multiplayer.ClientLevel;

import java.util.Objects;

public class WorldLeaveEvent extends EventArgument {

    public final ClientLevel world;
    public final boolean disconnected;

    public WorldLeaveEvent(ClientLevel world, boolean disconnected) {
        this.world = world;
        this.disconnected = disconnected;
    }

    @Override
    public void call(EventListener listener) {
        Objects.requireNonNull(listener).onWorldLeave(this);
    }
}
