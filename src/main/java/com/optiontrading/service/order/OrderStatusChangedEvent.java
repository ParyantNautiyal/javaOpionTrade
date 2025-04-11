package com.optiontrading.service.order;

import com.optiontrading.events.Event;
import com.optiontrading.service.model.OrderStatus;

/**
 * Event fired when an order's status changes
 */
public class OrderStatusChangedEvent extends Event {
    private final String orderId;
    private final OrderStatus oldStatus;
    private final OrderStatus newStatus;

    /**
     * Create a new event
     * 
     * @param orderId   the order ID
     * @param oldStatus the old status
     * @param newStatus the new status
     */
    public OrderStatusChangedEvent(String orderId, OrderStatus oldStatus, OrderStatus newStatus) {
        this.orderId = orderId;
        this.oldStatus = oldStatus;
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
     * Get the old status
     * 
     * @return the old status
     */
    public OrderStatus getOldStatus() {
        return oldStatus;
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