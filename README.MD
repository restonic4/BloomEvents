# Bloom Events

This utility library was created for the work in progress Bloom game engine.

With this library you can declare events, fire them and subscribe to them n times.

# How to create an event

## Manual way

You can declare an event by creating a FunctionalInterface and calling the EventFactory:
```java
public static final Event<PlayerDamaged> PLAYER_DAMAGED = EventFactory.createArray(PlayerDamaged.class, callbacks -> () -> {
    for (PlayerDamaged callback : callbacks) {
        callback.onEvent();
    }
});

@FunctionalInterface
public interface PlayerDamaged {
    void onEvent();
}
```

## Annotation util

Or you can use our annotation to make it a bit less verbose:
```java
public class WindowEvents extends WindowEventsBase {
    @Event(cancellable = true)
    public interface Moved {
        EventResult onEvent(Window window, int oldX, int oldY, int newX, int newY);
    }
}
```

You need to extend a class, in this case is your class name + Base, then, when you build, you will be able to use your events.
Each time you add or modify an event, you should build to reflect the changes, otherwise your IDE will complain.