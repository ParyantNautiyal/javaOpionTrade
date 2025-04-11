package com.optiontrading.service.order;

import com.optiontrading.events.Event;

/**
 * Event fired when an order is deleted
 */
public class OrderDeletedEvent extends Event {
    private final String orderId;

    /**
     * Create a new event
     * 
     * @param orderId the order ID
     */
    public OrderDeletedEvent(String orderId) {
        this.orderId = orderId;
    }

    /**
     * Get the order ID
     * 
     * @return the order ID
     */
    public String getOrderId() {
        return orderId;
    }
}