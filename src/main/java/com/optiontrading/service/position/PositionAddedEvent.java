package com.optiontrading.service.position;

import com.optiontrading.events.Event;

/**
 * Event published when a position is added to the watchlist
 */
public class PositionAddedEvent extends Event {
    private final WatchedPosition position;

    /**
     * Create a new position added event
     * 
     * @param position the position that was added
     */
    public PositionAddedEvent(WatchedPosition position) {
        this.position = position;
    }

    /**
     * Get the position that was added
     * 
     * @return the position
     */
    public WatchedPosition getPosition() {
        return position;
    }
}