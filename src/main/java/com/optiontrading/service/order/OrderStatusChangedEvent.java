package com.optiontrading.service.order;

import com.optiontrading.events.Event;
import com.optiontrading.service.model.OrderStatus;

/**
 * Event published when an order's status changes
 */
public class OrderStatusChangedEvent extends Event {
    private final String orderId;
    private final OrderStatus newStatus;

    /**
     * Constructor
     * 
     * @param orderId   the order ID
     * @param newStatus the new status
     */
    public OrderStatusChangedEvent(String orderId, OrderStatus newStatus) {
        super();
        this.orderId = orderId;
        this.newStatus = newStatus;
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
     * Get the new status
     * 
     * @return the new status
     */
    public OrderStatus getNewStatus() {
        return newStatus;
    }
}