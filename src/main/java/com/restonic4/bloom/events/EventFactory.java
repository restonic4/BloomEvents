package com.restonic4.bloom.events;

import java.util.function.Function;

public class EventFactory {
    public static <T> Event<T> createArray(Class<T> type, Function<T[], T> invokerFactory) {
        return new Event<>(invokerFactory, type);
    }
}