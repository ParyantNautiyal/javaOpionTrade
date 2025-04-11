package com.optiontrading.service.order;

import com.optiontrading.events.Event;
import com.optiontrading.service.model.OptionPair;

/**
 * Event fired when hedge orders are placed
 */
public class HedgeOrdersPlacedEvent extends Event {
    private final String orderId;
    private final OptionPair optionPair;

    /**
     * Create a new event
     * 
     * @param orderId    the order ID
     * @param optionPair the option pair used for hedging
     */
    public HedgeOrdersPlacedEvent(String orderId, OptionPair optionPair) {
        this.orderId = orderId;
        this.optionPair = optionPair;
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
     * Get the option pair
     * 
     * @return the option pair
     */
    public OptionPair getOptionPair() {
        return optionPair;
    }
}