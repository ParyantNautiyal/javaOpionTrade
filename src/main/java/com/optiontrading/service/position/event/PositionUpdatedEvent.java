package com.optiontrading.service.position.event;

import com.optiontrading.service.position.PositionEvent;
import com.optiontrading.service.position.WatchedPosition;

/**
 * Event published when a position is updated
 */
public class PositionUpdatedEvent extends PositionEvent {
    private final WatchedPosition position;

    public PositionUpdatedEvent(WatchedPosition position) {
        super(position.getId(), PositionEventType.UPDATED);
        this.position = position;
    }

    public WatchedPosition getPosition() {
        return position;
    }

    @Override
    public String toString() {
        return "PositionUpdatedEvent[position=" + position.getId() + "]";
    }
}