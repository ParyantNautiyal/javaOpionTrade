package com.optiontrading.events;

import java.math.BigDecimal;

/**
 * Event for market data updates.
 */
public class MarketDataEvent extends Event {
    private final String symbol;
    private final BigDecimal lastPrice;
    private final BigDecimal bidPrice;
    private final BigDecimal askPrice;
    private final long volume;

    /**
     * Create a new market data event
     * 
     * @param symbol    The symbol identifier
     * @param lastPrice The last traded price
     * @param bidPrice  The bid price
     * @param askPrice  The ask price
     * @param volume    The trading volume
     */
    public MarketDataEvent(String symbol, BigDecimal lastPrice, BigDecimal bidPrice, BigDecimal askPrice, long volume) {
        this.symbol = symbol;
        this.lastPrice = lastPrice;
        this.bidPrice = bidPrice;
        this.askPrice = askPrice;
        this.volume = volume;
    }

    /**
     * Get the symbol identifier
     * 
     * @return The symbol
     */
    public String getSymbol() {
        return symbol;
    }

    /**
     * Get the last traded price
     * 
     * @return The last price
     */
    public BigDecimal getLastPrice() {
        return lastPrice;
    }

    /**
     * Get the bid price
     * 
     * @return The bid price
     */
    public BigDecimal getBidPrice() {
        return bidPrice;
    }

    /**
     * Get the ask price
     * 
     * @return The ask price
     */
    public BigDecimal getAskPrice() {
        return askPrice;
    }

    /**
     * Get the trading volume
     * 
     * @return The volume
     */
    public long getVolume() {
        return volume;
    }

    @Override
    public String toString() {
        return "MarketDataEvent{" +
                "symbol='" + symbol + '\'' +
                ", lastPrice=" + lastPrice +
                ", bidPrice=" + bidPrice +
                ", askPrice=" + askPrice +
                ", volume=" + volume +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}