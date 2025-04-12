package com.optiontrading.service.trading;

import com.optiontrading.events.Event;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OrderType;

import java.math.BigDecimal;

/**
 * Event published when an order is successfully placed with the broker
 */
public class OrderPlacedEvent extends Event {
    private final String strategyId;
    private final Instrument instrument;
    private final String brokerId;
    private final OrderType orderType;
    private final int quantity;
    private final BigDecimal price;

    /**
     * Create a new order placed event
     *
     * @param strategyId the strategy ID
     * @param instrument the instrument that was traded
     * @param brokerId   the broker's order ID
     * @param orderType  the order type (BUY/SELL)
     * @param quantity   the quantity in lots
     * @param price      the order price
     */
    public OrderPlacedEvent(String strategyId, Instrument instrument, String brokerId,
            OrderType orderType, int quantity, BigDecimal price) {
        this.strategyId = strategyId;
        this.instrument = instrument;
        this.brokerId = brokerId;
        this.orderType = orderType;
        this.quantity = quantity;
        this.price = price;
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
     * Get the broker's order ID
     *
     * @return the broker's order ID
     */
    public String getBrokerId() {
        return brokerId;
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
     * Get the quantity in lots
     *
     * @return the quantity
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * Get the order price
     *
     * @return the price
     */
    public BigDecimal getPrice() {
        return price;
    }
}