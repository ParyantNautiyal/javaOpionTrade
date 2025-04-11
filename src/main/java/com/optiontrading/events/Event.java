package com.optiontrading.events;

import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Base class for all events in the system
 */
public abstract class Event {
    private final String eventId;
    private final LocalDateTime timestamp;

    /**
     * Create a new event
     */
    public Event() {
        this.eventId = UUID.randomUUID().toString();
        this.timestamp = LocalDateTime.now();
    }

    /**
     * Get the event ID
     * 
     * @return the event ID
     */
    public String getEventId() {
        return eventId;
    }

    /**
     * Get the timestamp when the event was created
     * 
     * @return the timestamp
     */
    public LocalDateTime getTimestamp() {
        return timestamp;
    }

    /**
     * Get the type of this event as a string
     * 
     * @return The event type name
     */
    public String getEventType() {
        return this.getClass().getSimpleName();
    }
}