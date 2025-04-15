package com.optiontrading.service.order;

import com.optiontrading.events.Event;

/**
 * Event triggered when an order is cleaned up, either after completion or
 * failure
 */
public class OrderCleanupEvent extends Event {
    private final String orderId;
    private final String details;

    /**
     * Constructor
     * 
     * @param orderId The order ID
     * @param details Details about the cleanup
     */
    public OrderCleanupEvent(String orderId, String details) {
        super();
        this.orderId = orderId;
        this.details = details;
    }

    /**
     * Get the order ID
     * 
     * @return the order ID
     */
    public String getOrderId() {
        return orderId;
    }

    /**
     * Get the cleanup details
     * 
     * @return the details
     */
    public String getDetails() {
        return details;
    }
}