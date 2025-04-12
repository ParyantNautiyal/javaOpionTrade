package com.optiontrading.service.trading;

import com.optiontrading.events.Event;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OrderType;

/**
 * Event published when an order placement fails
 */
public class OrderFailedEvent extends Event {
    private final String strategyId;
    private final Instrument instrument;
    private final OrderType orderType;
    private final String errorMessage;

    /**
     * Create a new order failed event
     *
     * @param strategyId   the strategy ID
     * @param instrument   the instrument that failed to trade
     * @param orderType    the order type (BUY/SELL)
     * @param errorMessage the error message
     */
    public OrderFailedEvent(String strategyId, Instrument instrument, OrderType orderType, String errorMessage) {
        this.strategyId = strategyId;
        this.instrument = instrument;
        this.orderType = orderType;
        this.errorMessage = errorMessage;
    }

    /**
     * Get the strategy ID
     *
     * @return the strategy ID
     */
    public String getStrategyId() {
        return strategyId;
    }

    /**
     * Get the instrument
     *
     * @return the instrument
     */
    public Instrument getInstrument() {
        return instrument;
    }

    /**
     * Get the order type
     *
     * @return the order type
     */
    public OrderType getOrderType() {
        return orderType;
    }

    /**
     * Get the error message
     *
     * @return the error message
     */
    public String getErrorMessage() {
        return errorMessage;
    }
}