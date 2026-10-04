package com.restonic4.bloom.events;

/**
 * Represents the result of a cancellable event.
 */
public enum EventResult {
    /**
     * Stops further event processing.
     */
    CANCELED,

    /**
     * Indicates that the event completed successfully.
     */
    SUCCEEDED,

    /**
     * Continues event processing.
     */
    CONTINUE
}