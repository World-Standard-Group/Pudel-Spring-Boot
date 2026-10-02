/*
 * Pudel - A Moderate Discord Chat Bot
 * Copyright (C) 2026 World Standard Group
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
package group.worldstandard.pudel.core.event;

import net.dv8tion.jda.api.JDA;
import net.dv8tion.jda.api.events.Event;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import group.worldstandard.pudel.api.event.EventHandler;
import group.worldstandard.pudel.api.event.EventPriority;
import group.worldstandard.pudel.api.event.Listener;
import group.worldstandard.pudel.api.event.PluginEventListener;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.mock;

/**
 * Tests that plugin event listeners are properly source-scoped and cannot
 * eavesdrop on events from other plugins or raw Discord events.
 * <p>
 * This test verifies the fix for issue #37 (Eavesdropping Listener).
 */
class PluginEventManagerEavesdroppingTest {

    private PluginEventManager eventManager;
    private List<String> invokedPlugins;
    private final JDA mockJda = mock(JDA.class);

    @BeforeEach
    void setUp() {
        eventManager = new PluginEventManager();
        invokedPlugins = new ArrayList<>();
    }

    // =========================================================================
    // Helper: a generic JDA event for testing
    // =========================================================================

    static class TestEvent extends Event {
        private final String name;

        public TestEvent(JDA jda, String name) {
            super(jda, 0);
            this.name = name;
        }

        public String getName() { return name; }
    }

    private TestEvent createEvent(String name) {
        return new TestEvent(mockJda, name);
    }

    // =========================================================================
    // Test 1: Same-plugin delivery works
    // =========================================================================

    @Test
    @DisplayName("A plugin receives events dispatched from its own interactions")
    void samePluginReceivesOwnEvents() {
        AtomicBoolean pluginAReceived = new AtomicBoolean(false);

        // Plugin A registers a listener for TestEvent
        Listener pluginAListener = new Listener() {
            @EventHandler(priority = EventPriority.NORMAL)
            public void onTestEvent(TestEvent event) {
                pluginAReceived.set(true);
                invokedPlugins.add("pluginA");
            }
        };
        eventManager.registerListener(pluginAListener, "pluginA");

        // Dispatch a TestEvent scoped to pluginA
        TestEvent event = createEvent("test-1");
        eventManager.dispatchEvent(event, "pluginA");

        assertTrue(pluginAReceived.get(), "Plugin A should receive its own event");
        assertEquals(1, invokedPlugins.size(), "Only one handler should be invoked");
        assertEquals("pluginA", invokedPlugins.getFirst());
    }

    // =========================================================================
    // Test 2: Cross-plugin eavesdropping is blocked
    // =========================================================================

    @Test
    @DisplayName("Plugin A does NOT receive events from Plugin B's interactions")
    void crossPluginEavesdroppingIsBlocked() {
        AtomicBoolean pluginAReceived = new AtomicBoolean(false);
        AtomicBoolean pluginBReceived = new AtomicBoolean(false);

        // Plugin A registers a listener
        Listener pluginAListener = new Listener() {
            @EventHandler(priority = EventPriority.NORMAL)
            public void onTestEvent(TestEvent event) {
                pluginAReceived.set(true);
                invokedPlugins.add("pluginA");
            }
        };
        eventManager.registerListener(pluginAListener, "pluginA");

        // Plugin B registers a listener for the same event type
        Listener pluginBListener = new Listener() {
            @EventHandler(priority = EventPriority.NORMAL)
            public void onTestEvent(TestEvent event) {
                pluginBReceived.set(true);
                invokedPlugins.add("pluginB");
            }
        };
        eventManager.registerListener(pluginBListener, "pluginB");

        // Dispatch a TestEvent scoped to Plugin B only
        TestEvent event = createEvent("test-2");
        eventManager.dispatchEvent(event, "pluginB");

        // Plugin B should receive the event
        assertTrue(pluginBReceived.get(), "Plugin B should receive its own event");

        // Plugin A should NOT receive Plugin B's event (no eavesdropping!)
        assertFalse(pluginAReceived.get(), "Plugin A must NOT eavesdrop on Plugin B's event");

        // Only pluginB should have been invoked
        assertEquals(1, invokedPlugins.size(), "Only one handler should be invoked");
        assertEquals("pluginB", invokedPlugins.getFirst());
    }

    // =========================================================================
    // Test 3: No-source events are blocked (raw Discord events)
    // =========================================================================

    @Test
    @DisplayName("Events dispatched without a source plugin reach all listeners (raw Discord monitoring)")
    void noSourceEventsAreDeliveredToAll() {
        AtomicBoolean pluginAReceived = new AtomicBoolean(false);
        AtomicBoolean pluginBReceived = new AtomicBoolean(false);

        // Plugin A registers a listener
        Listener pluginAListener = new Listener() {
            @EventHandler(priority = EventPriority.NORMAL)
            public void onTestEvent(TestEvent event) {
                pluginAReceived.set(true);
            }
        };
        eventManager.registerListener(pluginAListener, "pluginA");

        // Plugin B registers a listener
        Listener pluginBListener = new Listener() {
            @EventHandler(priority = EventPriority.NORMAL)
            public void onTestEvent(TestEvent event) {
                pluginBReceived.set(true);
            }
        };
        eventManager.registerListener(pluginBListener, "pluginB");

        // Dispatch a TestEvent WITHOUT a source plugin (simulates raw Discord event)
        TestEvent event = createEvent("test-3");
        eventManager.dispatchEvent(event);

        // Both plugins should receive the event (raw Discord monitoring)
        assertTrue(pluginAReceived.get(), "Plugin A should receive raw Discord events");
        assertTrue(pluginBReceived.get(), "Plugin B should receive raw Discord events");
    }

    // =========================================================================
    // Test 4: Typed PluginEventListener is also scoped
    // =========================================================================

    @Test
    @DisplayName("Typed PluginEventListener is also source-scoped")
    void typedEventListenerIsScoped() {
        AtomicBoolean pluginAReceived = new AtomicBoolean(false);
        AtomicBoolean pluginBReceived = new AtomicBoolean(false);

        // Plugin A registers a typed listener
        PluginEventListener<TestEvent> pluginAListener = new PluginEventListener<>() {
            @Override
            public Class<TestEvent> getEventClass() {
                return TestEvent.class;
            }

            @Override
            public void onEvent(TestEvent event) {
                pluginAReceived.set(true);
            }
        };
        eventManager.registerEventListener(pluginAListener, "pluginA");

        // Plugin B registers a typed listener
        PluginEventListener<TestEvent> pluginBListener = new PluginEventListener<>() {
            @Override
            public Class<TestEvent> getEventClass() {
                return TestEvent.class;
            }

            @Override
            public void onEvent(TestEvent event) {
                pluginBReceived.set(true);
            }
        };
        eventManager.registerEventListener(pluginBListener, "pluginB");

        // Dispatch event scoped to Plugin A
        TestEvent eventA = createEvent("test-4a");
        eventManager.dispatchEvent(eventA, "pluginA");

        assertTrue(pluginAReceived.get(), "Plugin A should receive its own typed event");
        assertFalse(pluginBReceived.get(), "Plugin B must NOT eavesdrop on Plugin A's typed event");

        // Reset and dispatch to Plugin B
        pluginAReceived.set(false);
        pluginBReceived.set(false);

        TestEvent eventB = createEvent("test-4b");
        eventManager.dispatchEvent(eventB, "pluginB");

        assertFalse(pluginAReceived.get(), "Plugin A must NOT eavesdrop on Plugin B's typed event");
        assertTrue(pluginBReceived.get(), "Plugin B should receive its own typed event");
    }

    // =========================================================================
    // Test 5: Multiple handlers in same plugin all fire
    // =========================================================================

    @Test
    @DisplayName("Multiple handlers in the same plugin all fire for a scoped event")
    void multipleHandlersInSamePluginFire() {
        AtomicInteger handler1Count = new AtomicInteger(0);
        AtomicInteger handler2Count = new AtomicInteger(0);

        // Plugin A registers a listener with two handlers for the same event
        Listener listener = new Listener() {
            @EventHandler(priority = EventPriority.HIGH)
            public void handler1(TestEvent event) {
                handler1Count.incrementAndGet();
            }

            @EventHandler(priority = EventPriority.LOW)
            public void handler2(TestEvent event) {
                handler2Count.incrementAndGet();
            }
        };
        eventManager.registerListener(listener, "pluginA");

        // Dispatch scoped to pluginA
        TestEvent event = createEvent("test-5");
        eventManager.dispatchEvent(event, "pluginA");

        assertEquals(1, handler1Count.get(), "handler1 should fire once");
        assertEquals(1, handler2Count.get(), "handler2 should fire once");
    }

    // =========================================================================
    // Test 6: Inheritance-based handler matching respects scoping
    // =========================================================================

    @Test
    @DisplayName("Inheritance-based handler matching also respects source scoping")
    void inheritanceMatchingRespectsScoping() {
        AtomicBoolean pluginAReceived = new AtomicBoolean(false);
        AtomicBoolean pluginBReceived = new AtomicBoolean(false);

        // Plugin A registers for the parent event type
        Listener pluginAListener = new Listener() {
            @EventHandler(priority = EventPriority.NORMAL)
            public void onTestEvent(TestEvent event) {
                pluginAReceived.set(true);
                invokedPlugins.add("pluginA");
            }
        };
        eventManager.registerListener(pluginAListener, "pluginA");

        // Plugin B registers for the same parent type
        Listener pluginBListener = new Listener() {
            @EventHandler(priority = EventPriority.NORMAL)
            public void onTestEvent(TestEvent event) {
                pluginBReceived.set(true);
                invokedPlugins.add("pluginB");
            }
        };
        eventManager.registerListener(pluginBListener, "pluginB");

        // Dispatch a subclass event scoped to pluginB
        class SubEvent extends TestEvent {
            SubEvent(JDA jda) { super(jda, "sub"); }
        }
        SubEvent event = new SubEvent(mockJda);
        eventManager.dispatchEvent(event, "pluginB");

        assertTrue(pluginBReceived.get(), "Plugin B should receive events matching through inheritance");
        assertFalse(pluginAReceived.get(), "Plugin A must NOT eavesdrop via inheritance-based matching");
    }
}
