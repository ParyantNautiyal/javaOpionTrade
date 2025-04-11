package com.optiontrading.service.order;

import com.optiontrading.events.Event;
import com.optiontrading.service.model.ScheduledOrder;

/**
 * Event fired when a new order is created
 */
public class OrderCreatedEvent extends Event {
    private final ScheduledOrder order;

    /**
     * Create a new event
     * 
     * @param order the created order
     */
    public OrderCreatedEvent(ScheduledOrder order) {
        this.order = order;
    }

    /**
     * Get the created order
     * 
     * @return the order
     */
    public ScheduledOrder getOrder() {
        return order;
    }
}