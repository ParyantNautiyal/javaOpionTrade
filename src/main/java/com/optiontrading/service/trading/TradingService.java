package com.optiontrading.service.trading;

import com.optiontrading.service.api.KiteConnectClient;
import com.optiontrading.service.api.TradingApiClient;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OrderStatus;
import com.optiontrading.service.model.OrderType;
import com.optiontrading.events.EventBus;

import java.math.BigDecimal;
import java.util.Map;
import java.util.HashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for executing trades through the broker API
 */
public class TradingService {
    private static final Logger LOGGER = Logger.getLogger(TradingService.class.getName());
    private static final TradingService INSTANCE = new TradingService();

    private final TradingApiClient tradingApiClient;
    private final EventBus eventBus;

    // Store order IDs and their status
    private final Map<String, String> orderIdMap = new ConcurrentHashMap<>();

    // Order placement retry settings
    private static final int MAX_RETRIES = 3;
    private static final long RETRY_DELAY_MS = 1000;

    private TradingService() {
        this.tradingApiClient = KiteConnectClient.getInstance();
        this.eventBus = EventBus.getInstance();
    }

    public static TradingService getInstance() {
        return INSTANCE;
    }

    /**
     * Place an order for an instrument
     * 
     * @param instrument the instrument to trade
     * @param quantity   the quantity (in lots)
     * @param price      the price (null for market orders)
     * @param orderType  BUY or SELL
     * @param strategyId the ID of the strategy/order this trade is part of
     * @return the broker order ID or null if failed
     */
    public String placeOrder(Instrument instrument, int quantity, BigDecimal price,
            OrderType orderType, String strategyId) {
        LOGGER.info(String.format("Placing %s order for %s, quantity: %d, price: %s",
                orderType, instrument.getTradingSymbol(), quantity, price));

        try {
            // Calculate actual quantity based on lot size
            int actualQuantity = quantity;
            if (instrument.getLotSize() > 0) {
                actualQuantity = quantity * instrument.getLotSize();
            }

            boolean isBuy = (orderType == OrderType.BUY);
            String brokerId = null;
            int retries = 0;

            while (brokerId == null && retries < MAX_RETRIES) {
                try {
                    brokerId = tradingApiClient.placeOrder(
                            instrument.getInstrumentId(),
                            actualQuantity,
                            price,
                            isBuy);

                    if (brokerId != null) {
                        // Store the mapping between our strategy ID and broker order ID
                        orderIdMap.put(strategyId + ":" + instrument.getInstrumentId(), brokerId);

                        LOGGER.info(String.format("Order placed successfully. Broker ID: %s, Strategy ID: %s",
                                brokerId, strategyId));

                        // Publish order placed event
                        eventBus.publishAsync(
                                new OrderPlacedEvent(strategyId, instrument, brokerId, orderType, quantity, price));

                        return brokerId;
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error placing order, retrying: " + e.getMessage(), e);
                    retries++;

                    if (retries < MAX_RETRIES) {
                        try {
                            Thread.sleep(RETRY_DELAY_MS);
                        } catch (InterruptedException ie) {
                            Thread.currentThread().interrupt();
                            LOGGER.warning("Interrupted during order retry delay");
                        }
                    }
                }
            }

            if (brokerId == null) {
                LOGGER.severe(String.format("Failed to place order after %d attempts: %s",
                        MAX_RETRIES, instrument.getTradingSymbol()));

                // Publish failure event
                eventBus.publishAsync(new OrderFailedEvent(strategyId, instrument, orderType, "Max retries exceeded"));
            }

            return brokerId;

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error placing order", e);

            // Publish failure event
            eventBus.publishAsync(new OrderFailedEvent(strategyId, instrument, orderType, e.getMessage()));

            return null;
        }
    }

    /**
     * Get order status from broker
     * 
     * @param brokerId the broker's order ID
     * @return the order status
     */
    public OrderStatus getOrderStatus(String brokerId) {
        // TODO: Implement order status checking with the broker API
        return OrderStatus.SCHEDULED; // Default status for now, using valid enum value
    }

    /**
     * Cancel an order
     * 
     * @param brokerId the broker's order ID
     * @return true if successfully cancelled
     */
    public boolean cancelOrder(String brokerId) {
        LOGGER.info("Cancelling order: " + brokerId);

        try {
            // TODO: Implement order cancellation with the broker API
            return false;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error cancelling order: " + brokerId, e);
            return false;
        }
    }

    /**
     * Get available margin
     * 
     * @return available margin amount
     */
    public BigDecimal getAvailableMargin() {
        try {
            Map<String, Object> margins = tradingApiClient.getMargins();

            if (margins != null && margins.containsKey("available")) {
                Object available = margins.get("available");
                if (available instanceof Map) {
                    Map<?, ?> availableMap = (Map<?, ?>) available;
                    if (availableMap.containsKey("cash")) {
                        return new BigDecimal(availableMap.get("cash").toString());
                    }
                }
            }

            LOGGER.warning("Could not determine available margin from API response");
            return BigDecimal.ZERO;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting margin information", e);
            return BigDecimal.ZERO;
        }
    }

    /**
     * Get total utilized margin
     * 
     * @return utilized margin amount
     */
    public BigDecimal getUtilizedMargin() {
        try {
            Map<String, Object> margins = tradingApiClient.getMargins();

            if (margins != null && margins.containsKey("utilised")) {
                Object utilized = margins.get("utilised");
                if (utilized instanceof Map) {
                    Map<?, ?> utilizedMap = (Map<?, ?>) utilized;
                    if (utilizedMap.containsKey("debits")) {
                        return new BigDecimal(utilizedMap.get("debits").toString());
                    }
                }
            }

            LOGGER.warning("Could not determine utilized margin from API response");
            return BigDecimal.ZERO;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error getting margin information", e);
            return BigDecimal.ZERO;
        }
    }
}