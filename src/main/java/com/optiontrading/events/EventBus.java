package com.optiontrading.events;

import com.optiontrading.resources.ThreadManager;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Event bus for publishing and subscribing to events
 */
@Singleton
public class EventBus {
    private static final Logger LOGGER = Logger.getLogger(EventBus.class.getName());

    // Map of event class to subscribers
    private final Map<Class<? extends Event>, Set<EventSubscriber<?>>> subscribers = new ConcurrentHashMap<>();

    // Thread pool for async event handling
    private final ExecutorService asyncExecutor;

    /**
     * Constructor with dependency injection
     * 
     * @param threadManager the thread manager to use for creating thread pools
     */
    @Inject
    public EventBus(ThreadManager threadManager) {
        this.asyncExecutor = threadManager.createFixedThreadPool("EventBus", 4);
        LOGGER.info("Initialized EventBus with dependency injection");
    }

    /**
     * Subscribe to events of a specific type
     * 
     * @param <T>        the event type
     * @param eventClass the event class
     * @param subscriber the subscriber
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> void subscribe(Class<T> eventClass, EventSubscriber<T> subscriber) {
        subscribers.computeIfAbsent(eventClass, k -> ConcurrentHashMap.newKeySet()).add(subscriber);
        LOGGER.info("Subscribed " + subscriber.getClass().getSimpleName() + " to " + eventClass.getSimpleName());
    }

    /**
     * Unsubscribe from events of a specific type
     * 
     * @param <T>        the event type
     * @param eventClass the event class
     * @param subscriber the subscriber
     */
    public <T extends Event> void unsubscribe(Class<T> eventClass, EventSubscriber<T> subscriber) {
        Set<EventSubscriber<?>> subs = subscribers.get(eventClass);
        if (subs != null) {
            subs.remove(subscriber);
            if (subs.isEmpty()) {
                subscribers.remove(eventClass);
            }
        }
    }

    /**
     * Publish an event synchronously
     * 
     * @param <T>   the event type
     * @param event the event to publish
     */
    @SuppressWarnings("unchecked")
    public <T extends Event> void publish(T event) {
        if (event == null)
            return;

        Class<? extends Event> eventClass = event.getClass();
        LOGGER.info("Publishing event: " + eventClass.getSimpleName());

        // Notify direct subscribers
        notifySubscribers(eventClass, event);

        // Notify parent class subscribers
        Class<?> parentClass = eventClass.getSuperclass();
        while (parentClass != null && Event.class.isAssignableFrom(parentClass)) {
            notifySubscribers((Class<? extends Event>) parentClass, event);
            parentClass = parentClass.getSuperclass();
        }
    }

    /**
     * Publish an event asynchronously
     * 
     * @param <T>   the event type
     * @param event the event to publish
     */
    public <T extends Event> void publishAsync(T event) {
        if (event == null)
            return;

        asyncExecutor.submit(() -> publish(event));
    }

    /**
     * Publish an event synchronously (alias for publish)
     * 
     * @param <T>   the event type
     * @param event the event to publish
     */
    public <T extends Event> void publishSync(T event) {
        publish(event);
    }

    /**
     * Notify subscribers of an event
     * 
     * @param <T>        the event type
     * @param eventClass the event class
     * @param event      the event
     */
    @SuppressWarnings("unchecked")
    private <T extends Event> void notifySubscribers(Class<? extends Event> eventClass, T event) {
        Set<EventSubscriber<?>> subs = subscribers.get(eventClass);
        if (subs != null && !subs.isEmpty()) {
            for (EventSubscriber<?> subscriber : subs) {
                try {
                    ((EventSubscriber<T>) subscriber).onEvent(event);
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error notifying subscriber", e);
                }
            }
        }
    }

    /**
     * Shutdown the event bus
     */
    public void shutdown() {
        LOGGER.info("Shutting down EventBus");
        subscribers.clear();
        // Don't shut down the executor - ResourceManager will handle that
    }
}