package com.optiontrading.events;

import java.math.BigDecimal;

/**
 * Event triggered when an instrument price is updated.
 */
public class PriceUpdateEvent extends Event {
    private final String instrumentId;
    private final BigDecimal price;

    /**
     * Constructor
     * 
     * @param instrumentId the instrument ID that was updated
     * @param price        the new price
     */
    public PriceUpdateEvent(String instrumentId, BigDecimal price) {
        super();
        this.instrumentId = instrumentId;
        this.price = price;
    }

    /**
     * Get the instrument ID
     * 
     * @return the instrument ID
     */
    public String getInstrumentId() {
        return instrumentId;
    }

    /**
     * Get the new price
     * 
     * @return the price
     */
    public BigDecimal getPrice() {
        return price;
    }

    @Override
    public String toString() {
        return "PriceUpdateEvent [instrumentId=" + instrumentId + ", price=" + price + "]";
    }
}