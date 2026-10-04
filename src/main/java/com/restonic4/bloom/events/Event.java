package com.restonic4.bloom.events;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Function;

/**
 * Stores event listeners and dispatches events through a shared invoker.
 *
 * @param <T> the listener type
 */
public class Event<T> {
    private final List<T> listeners = new ArrayList<>();
    private final Function<T[], T> invokerFactory;
    private final Class<T> type;
    private T invoker;

    /**
     * Creates a new event with the given listener invoker factory.
     *
     * @param invokerFactory factory used to create the event invoker
     * @param type listener class used to create the listener array
     */
    public Event(Function<T[], T> invokerFactory, Class<T> type) {
        this.invokerFactory = invokerFactory;
        this.type = type;
        this.invoker = invokerFactory.apply(createArray(0));
    }

    /**
     * Registers a listener for this event.
     *
     * @param listener the listener to register
     */
    public void register(T listener) {
        listeners.add(listener);
        updateInvoker();
    }

    private void updateInvoker() {
        invoker = invokerFactory.apply(listeners.toArray(createArray(listeners.size())));
    }

    /**
     * Returns the current event invoker.
     *
     * @return the event invoker
     */
    public T invoker() {
        return invoker;
    }

    @SuppressWarnings("unchecked")
    private T[] createArray(int length) {
        return (T[]) Array.newInstance(type, length);
    }
}