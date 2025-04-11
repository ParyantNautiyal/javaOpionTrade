package com.optiontrading.service.api;

import com.optiontrading.service.model.Instrument;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

/**
 * Interface for trading platform API clients
 */
public interface TradingApiClient {

    /**
     * Check if the client is authenticated
     * 
     * @return true if authenticated, false otherwise
     */
    boolean isAuthenticated();

    /**
     * Test the API connection
     * 
     * @return true if connection test is successful
     */
    boolean testConnection();

    /**
     * Get Last Traded Price (LTP) for multiple instruments
     * 
     * @param instrumentIds list of instrument IDs
     * @return map of instrument ID to price
     */
    Map<String, BigDecimal> getLTP(List<String> instrumentIds);

    /**
     * Get instruments from the API
     * 
     * @param exchange the exchange to get instruments for
     * @return list of instruments
     */
    List<Instrument> getInstruments(String exchange);

    /**
     * Place an order
     * 
     * @param instrumentId the instrument ID
     * @param quantity     the quantity
     * @param price        the price (null for market orders)
     * @param isBuy        true to buy, false to sell
     * @return the order ID
     */
    String placeOrder(String instrumentId, int quantity, BigDecimal price, boolean isBuy);
}