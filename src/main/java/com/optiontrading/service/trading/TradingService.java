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
 * Service for placing trades and managing orders
 * Implements singleton pattern
 */
public class TradingService {
    private static final Logger LOGGER = Logger.getLogger(TradingService.class.getName());

    // Singleton instance
    private static final TradingService INSTANCE = new TradingService();

    // API client
    private final KiteConnectClient kiteClient;

    // Event bus
    private final EventBus eventBus;

    // Private constructor for singleton
    private TradingService() {
        this.kiteClient = KiteConnectClient.getInstance();
        this.eventBus = EventBus.getInstance();

        LOGGER.info("Initialized TradingService");
    }

    /**
     * Get the singleton instance
     */
    public static TradingService getInstance() {
        return INSTANCE;
    }

    /**
     * Place an order
     * 
     * @param instrument the instrument to trade
     * @param quantity   the quantity to trade
     * @param price      the price to trade at (null for market orders)
     * @param orderType  the order type (BUY/SELL)
     * @param tag        an optional tag for the order (for reference)
     * @return the broker order ID, or null if the order failed
     */
    public String placeOrder(Instrument instrument, int quantity, BigDecimal price, OrderType orderType, String tag) {
        if (instrument == null) {
            LOGGER.warning("Cannot place order: instrument is null");
            return null;
        }

        if (quantity <= 0) {
            LOGGER.warning("Cannot place order: quantity must be positive");
            return null;
        }

        if (orderType == null) {
            LOGGER.warning("Cannot place order: order type is null");
            return null;
        }

        if (!kiteClient.isAuthenticated()) {
            LOGGER.severe("Cannot place order: not authenticated with broker");
            return null;
        }

        try {
            LOGGER.info("Placing " + orderType + " order for " + quantity + " of " +
                    instrument.getTradingSymbol() + " at price " +
                    (price != null ? price.toString() : "MARKET"));

            boolean isBuy = (orderType == OrderType.BUY);
            String orderId = kiteClient.placeOrder(
                    instrument.getInstrumentId(),
                    quantity,
                    price,
                    isBuy);

            if (orderId != null) {
                LOGGER.info("Order placed successfully, broker ID: " + orderId);

                // Publish event about the order
                eventBus.publishAsync(new OrderPlacedEvent(
                        orderId,
                        instrument,
                        quantity,
                        price,
                        orderType,
                        tag));
            } else {
                LOGGER.warning("Failed to place order, broker returned null ID");
            }

            return orderId;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error placing order", e);
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
            Map<String, Object> margins = kiteClient.getMargins();

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
            Map<String, Object> margins = kiteClient.getMargins();

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