package com.instrumentalist.krs.events.features;

import com.instrumentalist.krs.events.EventArgument;
import com.instrumentalist.krs.events.EventListener;
import java.util.Objects;

public class CancelBlockBreakingEvent extends EventArgument {
    @Override
    public void call(EventListener listener) {
        Objects.requireNonNull(listener).onCancelBlockBreaking(this);
    }
}