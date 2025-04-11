package com.optiontrading.service.market;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Interface for market data services
 */
public interface MarketDataService {

    /**
     * Start the market data service
     */
    void start();

    /**
     * Stop the market data service
     */
    void shutdown();

    /**
     * Subscribe to market data for instruments
     * 
     * @param instruments the list of instruments to subscribe to
     * @param subscriber  the subscriber to receive updates
     */
    void subscribe(List<String> instruments, MarketDataSubscriber subscriber);

    /**
     * Unsubscribe from market data for instruments
     * 
     * @param instruments the list of instruments to unsubscribe from
     * @param subscriber  the subscriber to remove
     */
    void unsubscribe(List<String> instruments, MarketDataSubscriber subscriber);

    /**
     * Get the last known price for an instrument
     * 
     * @param instrumentId the instrument ID
     * @return the last price or null if not available
     */
    BigDecimal getLastPrice(String instrumentId);

    /**
     * Get the last known prices for multiple instruments
     * 
     * @param instrumentIds the list of instrument IDs
     * @return map of instrument ID to last price
     */
    Map<String, BigDecimal> getLastPrices(List<String> instrumentIds);

    /**
     * Force a refresh of market data
     * 
     * @param instrumentIds the list of instrument IDs to refresh
     * @return map of instrument ID to updated price
     */
    Map<String, BigDecimal> refreshPrices(List<String> instrumentIds);
}