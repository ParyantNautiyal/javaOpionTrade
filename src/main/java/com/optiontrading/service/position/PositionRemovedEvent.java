package com.optiontrading.service.position;

import com.optiontrading.events.Event;

/**
 * Event published when a position is removed from the watchlist
 */
public class PositionRemovedEvent extends Event {
    private final WatchedPosition position;

    /**
     * Create a new position removed event
     * 
     * @param position the position that was removed
     */
    public PositionRemovedEvent(WatchedPosition position) {
        this.position = position;
    }

    /**
     * Get the position that was removed
     * 
     * @return the position
     */
    public WatchedPosition getPosition() {
        return position;
    }
}