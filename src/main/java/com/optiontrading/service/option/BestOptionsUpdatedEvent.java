package com.optiontrading.service.option;

import com.optiontrading.events.Event;
import com.optiontrading.service.model.OptionPair;

import java.util.List;

/**
 * Event that is published when the best options have been updated.
 */
public class BestOptionsUpdatedEvent extends Event {
    private final String orderId;
    private final List<OptionPair> bestOptions;

    /**
     * Create a new best options updated event.
     *
     * @param orderId     the order ID
     * @param bestOptions the best options
     */
    public BestOptionsUpdatedEvent(String orderId, List<OptionPair> bestOptions) {
        this.orderId = orderId;
        this.bestOptions = bestOptions;
    }

    /**
     * Get the order ID.
     *
     * @return the order ID
     */
    public String getOrderId() {
        return orderId;
    }

    /**
     * Get the best options.
     *
     * @return the best options
     */
    public List<OptionPair> getBestOptions() {
        return bestOptions;
    }

    /**
     * Get the first option pair (for backward compatibility).
     *
     * @return the first option pair or null if no options
     */
    public OptionPair getOptionPair() {
        return bestOptions != null && !bestOptions.isEmpty() ? bestOptions.get(0) : null;
    }
}