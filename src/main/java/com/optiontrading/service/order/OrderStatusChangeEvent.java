package com.optiontrading.service.order;

import com.optiontrading.events.Event;
import com.optiontrading.service.model.OrderStatus;

/**
 * Event triggered when an order's status changes
 */
public class OrderStatusChangeEvent extends Event {
    private final String orderId;
    private final OrderStatus oldStatus;
    private final OrderStatus newStatus;

    /**
     * Constructor
     * 
     * @param orderId   The order ID
     * @param oldStatus The old order status
     * @param newStatus The new order status
     */
    public OrderStatusChangeEvent(String orderId, OrderStatus oldStatus, OrderStatus newStatus) {
        super();
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