/*
 * Pudel Plugin API (PDK) - Plugin Development Kit for Pudel Discord Bot
 * Copyright (c) 2026 Napapon Kamanee
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy
 * of this software and associated documentation files (the "Software"), to deal
 * in the Software without restriction, including without limitation the rights
 * to use, copy, modify, merge, publish, distribute, sublicense, and/or sell
 * copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all
 * copies or substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
 * IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
 * FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT.
 */
package worldstandard.group.pudel.api.event;

/**
 * Event priority levels for plugin event listeners.
 * Higher priority listeners are called first.
 */
public enum EventPriority {
    /**
     * Called first, before all other priorities.
     * Use for monitoring/logging purposes.
     */
    MONITOR(100),

    /**
     * Called with highest priority among processing listeners.
     */
    HIGHEST(75),

    /**
     * Called with high priority.
     */
    HIGH(50),

    /**
     * Default priority level.
     */
    NORMAL(0),

    /**
     * Called with low priority.
     */
    LOW(-50),

    /**
     * Called last, after all other priorities.
     */
    LOWEST(-100);

    private final int value;

    EventPriority(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}

