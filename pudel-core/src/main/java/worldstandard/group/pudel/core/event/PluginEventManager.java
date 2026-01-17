/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 Napapon Kamanee
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU Affero General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed with an additional permission known as the
 * "Pudel Plugin Exception".
 *
 * See the LICENSE and PLUGIN_EXCEPTION files in the project root for details.
 */
package worldstandard.group.pudel.core.event;

import net.dv8tion.jda.api.events.GenericEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import worldstandard.group.pudel.api.event.EventHandler;
import worldstandard.group.pudel.api.event.EventManager;
import worldstandard.group.pudel.api.event.EventPriority;
import worldstandard.group.pudel.api.event.Listener;
import worldstandard.group.pudel.api.event.PluginEventListener;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Implementation of EventManager that handles plugin event registration and dispatch.
 */
@Component
public class PluginEventManager implements EventManager {

    private static final Logger logger = LoggerFactory.getLogger(PluginEventManager.class);

    // Map of event class -> list of registered handlers
    private final Map<Class<? extends GenericEvent>, List<RegisteredHandler>> eventHandlers = new ConcurrentHashMap<>();

    // Map of plugin name -> list of its registered handlers (for cleanup)
    private final Map<String, List<RegisteredHandler>> pluginHandlers = new ConcurrentHashMap<>();

    // Map of listener object -> list of its registered handlers
    private final Map<Listener, List<RegisteredHandler>> listenerHandlers = new ConcurrentHashMap<>();

    @Override
    public void registerListener(Listener listener, String pluginName) {
        if (listener == null || pluginName == null) {
            logger.warn("Cannot register null listener or plugin name");
            return;
        }

        List<RegisteredHandler> handlers = new ArrayList<>();

        // Scan for @EventHandler annotated methods
        for (Method method : listener.getClass().getDeclaredMethods()) {
            EventHandler annotation = method.getAnnotation(EventHandler.class);
            if (annotation == null) {
                continue;
            }

            // Validate method signature
            Class<?>[] params = method.getParameterTypes();
            if (params.length != 1) {
                logger.warn("Event handler method {} in {} must have exactly one parameter",
                        method.getName(), listener.getClass().getName());
                continue;
            }

            Class<?> eventType = params[0];
            if (!GenericEvent.class.isAssignableFrom(eventType)) {
                logger.warn("Event handler method {} in {} parameter must extend GenericEvent",
                        method.getName(), listener.getClass().getName());
                continue;
            }

            @SuppressWarnings("unchecked")
            Class<? extends GenericEvent> eventClass = (Class<? extends GenericEvent>) eventType;

            // Create registered handler
            RegisteredHandler handler = new RegisteredHandler(
                    pluginName,
                    listener,
                    method,
                    eventClass,
                    annotation.priority(),
                    annotation.ignoreCancelled()
            );

            handlers.add(handler);
            registerHandler(eventClass, handler);

            logger.debug("Registered event handler: {} for event {} in plugin {}",
                    method.getName(), eventClass.getSimpleName(), pluginName);
        }

        if (!handlers.isEmpty()) {
            listenerHandlers.put(listener, handlers);
            pluginHandlers.computeIfAbsent(pluginName, k -> new CopyOnWriteArrayList<>()).addAll(handlers);
            logger.info("Registered {} event handlers for plugin {}", handlers.size(), pluginName);
        }
    }

    @Override
    public <T extends GenericEvent> void registerEventListener(PluginEventListener<T> listener, String pluginName) {
        if (listener == null || pluginName == null) {
            logger.warn("Cannot register null event listener or plugin name");
            return;
        }

        RegisteredHandler handler = new RegisteredHandler(
                pluginName,
                listener,
                listener.getEventClass(),
                listener.getPriority(),
                listener.ignoreCancelled()
        );

        registerHandler(listener.getEventClass(), handler);
        pluginHandlers.computeIfAbsent(pluginName, k -> new CopyOnWriteArrayList<>()).add(handler);

        logger.info("Registered typed event listener for {} in plugin {}",
                listener.getEventClass().getSimpleName(), pluginName);
    }

    private void registerHandler(Class<? extends GenericEvent> eventClass, RegisteredHandler handler) {
        List<RegisteredHandler> handlers = eventHandlers.computeIfAbsent(eventClass, k -> new CopyOnWriteArrayList<>());
        handlers.add(handler);

        // Sort by priority (higher priority first)
        handlers.sort(Comparator.comparingInt((RegisteredHandler h) -> h.priority.getValue()).reversed());
    }

    @Override
    public void unregisterListeners(String pluginName) {
        if (pluginName == null) {
            return;
        }

        List<RegisteredHandler> handlers = pluginHandlers.remove(pluginName);
        if (handlers != null) {
            for (RegisteredHandler handler : handlers) {
                removeHandler(handler);
            }
            logger.info("Unregistered {} event handlers for plugin {}", handlers.size(), pluginName);
        }
    }

    @Override
    public void unregisterListener(Listener listener) {
        if (listener == null) {
            return;
        }

        List<RegisteredHandler> handlers = listenerHandlers.remove(listener);
        if (handlers != null) {
            for (RegisteredHandler handler : handlers) {
                removeHandler(handler);

                // Also remove from plugin handlers
                List<RegisteredHandler> pluginList = pluginHandlers.get(handler.pluginName);
                if (pluginList != null) {
                    pluginList.remove(handler);
                }
            }
            logger.debug("Unregistered listener with {} handlers", handlers.size());
        }
    }

    @Override
    public <T extends GenericEvent> void unregisterEventListener(PluginEventListener<T> listener) {
        if (listener == null) {
            return;
        }

        List<RegisteredHandler> handlers = eventHandlers.get(listener.getEventClass());
        if (handlers != null) {
            handlers.removeIf(h -> h.eventListener == listener);
        }
    }

    private void removeHandler(RegisteredHandler handler) {
        List<RegisteredHandler> handlers = eventHandlers.get(handler.eventClass);
        if (handlers != null) {
            handlers.remove(handler);
            if (handlers.isEmpty()) {
                eventHandlers.remove(handler.eventClass);
            }
        }
    }

    @Override
    public int getListenerCount(String pluginName) {
        List<RegisteredHandler> handlers = pluginHandlers.get(pluginName);
        return handlers != null ? handlers.size() : 0;
    }

    @Override
    public int getTotalHandlerCount() {
        return eventHandlers.values().stream().mapToInt(List::size).sum();
    }

    /**
     * Dispatches an event to all registered handlers.
     * This is called by the core event listener.
     *
     * @param event the JDA event
     */
    public void dispatchEvent(GenericEvent event) {
        if (event == null) {
            return;
        }

        // Find handlers for this exact event class
        List<RegisteredHandler> handlers = eventHandlers.get(event.getClass());

        if (handlers == null || handlers.isEmpty()) {
            // Also check parent classes for more general handlers
            for (Map.Entry<Class<? extends GenericEvent>, List<RegisteredHandler>> entry : eventHandlers.entrySet()) {
                if (entry.getKey().isAssignableFrom(event.getClass())) {
                    dispatchToHandlers(event, entry.getValue());
                }
            }
            return;
        }

        dispatchToHandlers(event, handlers);
    }

    private void dispatchToHandlers(GenericEvent event, List<RegisteredHandler> handlers) {
        for (RegisteredHandler handler : handlers) {
            try {
                handler.invoke(event);
            } catch (Exception e) {
                logger.error("Error dispatching event {} to handler in plugin {}: {}",
                        event.getClass().getSimpleName(), handler.pluginName, e.getMessage(), e);
            }
        }
    }

    /**
     * Gets the list of event types that have registered handlers.
     * @return list of event class names
     */
    public List<String> getRegisteredEventTypes() {
        return eventHandlers.keySet().stream()
                .map(Class::getSimpleName)
                .toList();
    }

    /**
     * Internal class representing a registered event handler.
     */
    private static class RegisteredHandler {
        final String pluginName;
        final Listener listener;
        final Method method;
        final PluginEventListener<?> eventListener;
        final Class<? extends GenericEvent> eventClass;
        final EventPriority priority;
        final boolean ignoreCancelled;

        // Constructor for annotation-based handlers
        RegisteredHandler(String pluginName, Listener listener, Method method,
                         Class<? extends GenericEvent> eventClass,
                         EventPriority priority, boolean ignoreCancelled) {
            this.pluginName = pluginName;
            this.listener = listener;
            this.method = method;
            this.eventListener = null;
            this.eventClass = eventClass;
            this.priority = priority;
            this.ignoreCancelled = ignoreCancelled;
            this.method.setAccessible(true);
        }

        // Constructor for typed event listeners
        RegisteredHandler(String pluginName, PluginEventListener<?> eventListener,
                         Class<? extends GenericEvent> eventClass,
                         int priority, boolean ignoreCancelled) {
            this.pluginName = pluginName;
            this.listener = null;
            this.method = null;
            this.eventListener = eventListener;
            this.eventClass = eventClass;
            this.priority = findClosestPriority(priority);
            this.ignoreCancelled = ignoreCancelled;
        }

        private EventPriority findClosestPriority(int value) {
            EventPriority closest = EventPriority.NORMAL;
            int minDiff = Integer.MAX_VALUE;
            for (EventPriority p : EventPriority.values()) {
                int diff = Math.abs(p.getValue() - value);
                if (diff < minDiff) {
                    minDiff = diff;
                    closest = p;
                }
            }
            return closest;
        }

        @SuppressWarnings("unchecked")
        void invoke(GenericEvent event) throws Exception {
            if (method != null && listener != null) {
                method.invoke(listener, event);
            } else if (eventListener != null) {
                ((PluginEventListener<GenericEvent>) eventListener).onEvent(event);
            }
        }
    }
}

