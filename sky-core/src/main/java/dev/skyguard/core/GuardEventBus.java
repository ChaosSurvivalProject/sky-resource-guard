/*
 * SPDX-License-Identifier: GPL-3.0-or-later
 * Copyright (C) 2026 SKY Resource Guard contributors
 */
package dev.skyguard.core;

import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;

/** Minimal in-process event bus. AI and audit subscribers are downstream only. */
public final class GuardEventBus {

    public record Event(String type, Instant at, String summary) {}

    private static final int MAX_RETAINED = 512;

    private final CopyOnWriteArrayList<Consumer<Event>> listeners = new CopyOnWriteArrayList<>();
    private final java.util.LinkedList<Event> recent = new java.util.LinkedList<>();

    public void subscribe(Consumer<Event> listener) {
        listeners.add(java.util.Objects.requireNonNull(listener, "listener"));
    }

    public void publish(String type, Instant at, String summary) {
        Event event = new Event(type, at, summary);
        synchronized (recent) {
            recent.addLast(event);
            while (recent.size() > MAX_RETAINED) {
                recent.removeFirst();
            }
        }
        for (Consumer<Event> listener : listeners) {
            try {
                listener.accept(event);
            } catch (RuntimeException ignored) {
                // A faulty observer must never break the guard loop.
            }
        }
    }

    public List<Event> recent() {
        synchronized (recent) {
            return List.copyOf(recent);
        }
    }
}
