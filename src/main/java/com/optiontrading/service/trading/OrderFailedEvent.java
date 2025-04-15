package com.optiontrading.service.trading;

import com.optiontrading.events.Event;
import com.optiontrading.service.model.OrderType;

/**
 * Event that is published when an order fails
 */
public class OrderFailedEvent extends Event {
    private final String orderId;
    private final String strategy;
    private final String instrumentSymbol;
    private final OrderType orderType;
    private final String errorMessage;

    /**
     * Constructor
     *
     * @param orderId          the order ID
     * @param strategy         the strategy or component that encountered the
     *                         failure
     * @param instrumentSymbol the instrument symbol
     * @param orderType        the order type
     * @param errorMessage     the error message
     */
    public OrderFailedEvent(String orderId, String strategy, String instrumentSymbol, OrderType orderType,
            String errorMessage) {
        super(); // Call Event constructor to initialize eventId and timestamp
        this.orderId = orderId;
        this.strategy = strategy;
        this.instrumentSymbol = instrumentSymbol;
        this.orderType = orderType;
        this.errorMessage = errorMessage;
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
     * Get the strategy
     *
     * @return the strategy
     */
    public String getStrategy() {
        return strategy;
    }

    /**
     * Get the instrument symbol
     *
     * @return the instrument symbol
     */
    public String getInstrumentSymbol() {
        return instrumentSymbol;
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

    @Override
    public String toString() {
        return "OrderFailedEvent{" +
                "eventId='" + getEventId() + '\'' +
                ", timestamp='" + getTimestamp() + '\'' +
                ", orderId='" + orderId + '\'' +
                ", strategy='" + strategy + '\'' +
                ", instrumentSymbol='" + instrumentSymbol + '\'' +
                ", orderType=" + orderType +
                ", errorMessage='" + errorMessage + '\'' +
                '}';
    }
}