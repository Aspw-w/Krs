package com.instrumentalist.krs.events;

import com.instrumentalist.krs.events.features.*;
import com.mojang.logging.LogUtils;
import org.slf4j.Logger;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class EventManager {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long FAILURE_LOG_INTERVAL_NANOS = 5_000_000_000L;
    private static final EventListener[] EMPTY_LISTENERS = new EventListener[0];
    private static final EventRoute[] EVENT_ROUTES = EventRoute.values();
    private static final Map<Class<? extends EventArgument>, EventRoute> ROUTE_BY_EVENT = createEventRouteMap();

    private final Set<EventListener> listenerRegistry;
    private final Map<Class<?>, Long> listenerFailureLogTimes = new HashMap<>();
    private volatile DispatchTable dispatchTable = DispatchTable.empty();

    public EventManager() {
        this.listenerRegistry = new LinkedHashSet<>();
    }

    public void call(final EventArgument argument) {
        if (argument == null)
            return;

        EventListener[] listeners = listenersFor(argument.getClass());
        if (listeners.length == 0)
            return;

        for (EventListener listener : listeners) {
            try {
                argument.call(listener);
            } catch (RuntimeException e) {
                reportListenerFailure(listener, e);
            }
        }
    }

    public void register(final EventListener listener) {
        if (listener == null)
            return;

        synchronized (listenerRegistry) {
            if (listenerRegistry.add(listener))
                rebuildSnapshots();
        }
    }

    public void unregister(final EventListener listener) {
        if (listener == null)
            return;

        synchronized (listenerRegistry) {
            if (listenerRegistry.remove(listener))
                rebuildSnapshots();
        }
    }

    public boolean hasListeners(Class<? extends EventArgument> eventClass) {
        return eventClass != null && listenersFor(eventClass).length > 0;
    }

    private EventListener[] listenersFor(Class<?> eventClass) {
        DispatchTable table = dispatchTable;
        EventRoute route = ROUTE_BY_EVENT.get(eventClass);
        return route == null ? table.allListeners() : table.routedListeners()[route.ordinal()];
    }

    private void reportListenerFailure(EventListener listener, RuntimeException failure) {
        Class<?> listenerClass = listener.getClass();
        long now = System.nanoTime();

        synchronized (listenerFailureLogTimes) {
            Long lastLogTime = listenerFailureLogTimes.get(listenerClass);
            if (lastLogTime != null && now - lastLogTime < FAILURE_LOG_INTERVAL_NANOS)
                return;

            listenerFailureLogTimes.put(listenerClass, now);
        }

        LOGGER.error("Event listener {} failed", listenerClass.getName(), failure);
    }

    private void rebuildSnapshots() {
        EnumMap<EventRoute, List<EventListener>> listenersByRoute = new EnumMap<>(EventRoute.class);
        for (EventRoute route : EVENT_ROUTES)
            listenersByRoute.put(route, new ArrayList<>());

        for (EventListener listener : listenerRegistry) {
            EnumSet<EventRoute> routes = ROUTES_BY_LISTENER.get(listener.getClass());
            for (EventRoute route : routes) {
                listenersByRoute.get(route).add(listener);
            }
        }

        EventListener[][] routedListeners = new EventListener[EVENT_ROUTES.length][];
        for (EventRoute route : EVENT_ROUTES)
            routedListeners[route.ordinal()] = listenersByRoute.get(route).toArray(EMPTY_LISTENERS);

        // Publish the fallback and routed snapshots together so dispatch never observes
        // a half-rebuilt listener table from another thread.
        dispatchTable = new DispatchTable(listenerRegistry.toArray(EMPTY_LISTENERS), routedListeners);
    }

    private static Map<Class<? extends EventArgument>, EventRoute> createEventRouteMap() {
        Map<Class<? extends EventArgument>, EventRoute> routes = new HashMap<>();
        for (EventRoute route : EVENT_ROUTES)
            routes.put(route.eventClass, route);
        return Map.copyOf(routes);
    }

    private enum EventRoute {
        KEY(KeyboardEvent.class, "onKey"),
        UPDATE(UpdateEvent.class, "onUpdate"),
        SEND_PACKET(SendPacketEvent.class, "onSendPacket"),
        RECEIVED_PACKET(ReceivedPacketEvent.class, "onReceivedPacket"),
        ATTACK(AttackEvent.class, "onAttack"),
        RENDER_HUD(RenderHudEvent.class, "onRenderHud"),
        HANDLE_INPUT(HandleInputEvent.class, "onHandleInput"),
        MOTION(MotionEvent.class, "onMotion"),
        WORLD(WorldEvent.class, "onWorld"),
        BLOCK_EDGE(BlockEdgeEvent.class, "onBlockEdge"),
        TICK(TickEvent.class, "onTick"),
        RENDER(RenderEvent.class, "onRender"),
        MOUSE_SCROLL(MouseScrollEvent.class, "onMouseScroll"),
        MOUSE_CLICK(MouseClickEvent.class, "onMouseClick"),
        MODIFY_PACKET(ModifyPacketEvent.class, "onModifyPacket"),
        FRAME_BUFFER(FrameBufferEvent.class, "onFrameBuffer"),
        RENDER_3D(Render3DEvent.class, "onRender3D"),
        BLOCK(BlockEvent.class, "onBlock"),
        CANCEL_BLOCK_BREAKING(CancelBlockBreakingEvent.class, "onCancelBlockBreaking");

        private final Class<? extends EventArgument> eventClass;
        private final String listenerMethodName;

        EventRoute(Class<? extends EventArgument> eventClass, String listenerMethodName) {
            this.eventClass = eventClass;
            this.listenerMethodName = listenerMethodName;
        }
    }

    private static final ClassValue<EnumSet<EventRoute>> ROUTES_BY_LISTENER = new ClassValue<>() {
        @Override
        protected EnumSet<EventRoute> computeValue(Class<?> listenerClass) {
            EnumSet<EventRoute> routes = EnumSet.noneOf(EventRoute.class);
            for (EventRoute route : EventRoute.values()) {
                if (overridesListenerMethod(listenerClass, route))
                    routes.add(route);
            }
            return routes;
        }
    };

    private static boolean overridesListenerMethod(Class<?> listenerClass, EventRoute route) {
        try {
            Method method = listenerClass.getMethod(route.listenerMethodName, route.eventClass);
            return method.getDeclaringClass() != EventListener.class;
        } catch (NoSuchMethodException ignored) {
            return false;
        }
    }

    private record DispatchTable(EventListener[] allListeners, EventListener[][] routedListeners) {
        private static DispatchTable empty() {
            EventListener[][] routedListeners = new EventListener[EVENT_ROUTES.length][];
            for (int i = 0; i < routedListeners.length; i++)
                routedListeners[i] = EMPTY_LISTENERS;
            return new DispatchTable(EMPTY_LISTENERS, routedListeners);
        }
    }
}
