package com.optiontrading.service.position;

import com.optiontrading.events.Event;

/**
 * Base event class for position-related events
 */
public class PositionEvent extends Event {
    private final String positionId;
    private final PositionEventType eventType;

    /**
     * Enum defining different types of position events
     */
    public enum PositionEventType {
        ADDED,
        UPDATED,
        REMOVED,
        STATUS_CHANGED,
        ORDER_EXECUTED
    }

    /**
     * Create a new position event
     * 
     * @param positionId the ID of the position
     * @param eventType  the type of event
     */
    public PositionEvent(String positionId, PositionEventType eventType) {
        super(); // Call Event constructor
        this.positionId = positionId;
        this.eventType = eventType;
    }

    /**
     * Get the position ID
     * 
     * @return the position ID
     */
    public String getPositionId() {
        return positionId;
    }

    /**
     * Get the position event type
     * 
     * @return the position event type
     */
    public PositionEventType getPositionEventType() {
        return eventType;
    }
}