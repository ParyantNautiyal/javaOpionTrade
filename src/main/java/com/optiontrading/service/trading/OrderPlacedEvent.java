package com.optiontrading.service.trading;

import com.optiontrading.events.Event;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OrderType;

import java.math.BigDecimal;

/**
 * Event published when an order is placed
 */
public class OrderPlacedEvent extends Event {
    private final String orderId;
    private final Instrument instrument;
    private final int quantity;
    private final BigDecimal price;
    private final OrderType orderType;
    private final String tag;

    /**
     * Create a new order placed event
     * 
     * @param orderId    the broker order ID
     * @param instrument the instrument that was traded
     * @param quantity   the quantity that was traded
     * @param price      the price that was used (null for market orders)
     * @param orderType  the order type (BUY/SELL)
     * @param tag        an optional tag for the order
     */
    public OrderPlacedEvent(String orderId, Instrument instrument, int quantity,
            BigDecimal price, OrderType orderType, String tag) {
        this.orderId = orderId;
        this.instrument = instrument;
        this.quantity = quantity;
        this.price = price;
        this.orderType = orderType;
        this.tag = tag;
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
     * Get the instrument
     * 
     * @return the instrument
     */
    public Instrument getInstrument() {
        return instrument;
    }

    /**
     * Get the quantity
     * 
     * @return the quantity
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * Get the price
     * 
     * @return the price
     */
    public BigDecimal getPrice() {
        return price;
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
     * Get the tag
     * 
     * @return the tag
     */
    public String getTag() {
        return tag;
    }
}