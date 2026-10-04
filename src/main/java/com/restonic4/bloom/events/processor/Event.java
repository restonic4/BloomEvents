package com.restonic4.bloom.events.processor;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks an interface as an event and allows the processor to generate its dispatcher.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Event {
    /**
     * Defines whether the event can stop further event processing.
     *
     * @return true when the event is cancellable
     */
    boolean cancellable() default false;
}