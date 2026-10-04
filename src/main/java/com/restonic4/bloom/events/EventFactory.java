package com.restonic4.bloom.events;

import java.util.function.Function;

/**
 * Creates event instances for a given listener type.
 */
public class EventFactory {
    /**
     * Creates a new event using the given listener type and invoker factory.
     *
     * @param type the listener type
     * @param invokerFactory factory used to create the event invoker
     * @param <T> the listener type
     * @return a new event instance
     */
    public static <T> Event<T> createArray(Class<T> type, Function<T[], T> invokerFactory) {
        return new Event<>(invokerFactory, type);
    }
}