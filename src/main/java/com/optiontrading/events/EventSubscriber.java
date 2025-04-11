package com.optiontrading.events;

/**
 * Interface for subscribing to events
 *
 * @param <T> the type of event
 */
public interface EventSubscriber<T extends Event> {

    /**
     * Called when an event is published
     * 
     * @param event the event
     */
    void onEvent(T event);
}