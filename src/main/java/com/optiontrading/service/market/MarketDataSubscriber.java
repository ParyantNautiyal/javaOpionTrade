package com.optiontrading.service.market;

import java.math.BigDecimal;

/**
 * Interface for components that want to receive market data updates
 */
public interface MarketDataSubscriber {

    /**
     * Called when a price update is received for an instrument
     * 
     * @param instrumentId the instrument ID
     * @param price        the updated price
     */
    void onPriceUpdate(String instrumentId, BigDecimal price);
}