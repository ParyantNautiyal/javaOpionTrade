package com.optiontrading.events;

import java.math.BigDecimal;

/**
 * Event for order status updates.
 */
public class OrderEvent extends Event {

    public enum OrderStatus {
        CREATED,
        PLACED,
        PARTIALLY_FILLED,
        FILLED,
        CANCELLED,
        REJECTED
    }

    private final String orderId;
    private final String symbol;
    private final boolean isBuy;
    private final BigDecimal price;
    private final int quantity;
    private final OrderStatus status;
    private final String message;

    /**
     * Create a new order event
     * 
     * @param orderId  The order identifier
     * @param symbol   The symbol identifier
     * @param isBuy    Whether this is a buy order
     * @param price    The order price
     * @param quantity The order quantity
     * @param status   The order status
     * @param message  Optional message (e.g. for rejection reason)
     */
    public OrderEvent(String orderId, String symbol, boolean isBuy, BigDecimal price,
            int quantity, OrderStatus status, String message) {
        this.orderId = orderId;
        this.symbol = symbol;
        this.isBuy = isBuy;
        this.price = price;
        this.quantity = quantity;
        this.status = status;
        this.message = message;
    }

    /**
     * Create a new order event without a message
     * 
     * @param orderId  The order identifier
     * @param symbol   The symbol identifier
     * @param isBuy    Whether this is a buy order
     * @param price    The order price
     * @param quantity The order quantity
     * @param status   The order status
     */
    public OrderEvent(String orderId, String symbol, boolean isBuy, BigDecimal price,
            int quantity, OrderStatus status) {
        this(orderId, symbol, isBuy, price, quantity, status, null);
    }

    /**
     * Get the order identifier
     * 
     * @return The order ID
     */
    public String getOrderId() {
        return orderId;
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
     * Check if this is a buy order
     * 
     * @return true if this is a buy order, false if it's a sell order
     */
    public boolean isBuy() {
        return isBuy;
    }

    /**
     * Get the order price
     * 
     * @return The price
     */
    public BigDecimal getPrice() {
        return price;
    }

    /**
     * Get the order quantity
     * 
     * @return The quantity
     */
    public int getQuantity() {
        return quantity;
    }

    /**
     * Get the order status
     * 
     * @return The status
     */
    public OrderStatus getStatus() {
        return status;
    }

    /**
     * Get the optional message
     * 
     * @return The message
     */
    public String getMessage() {
        return message;
    }

    @Override
    public String toString() {
        return "OrderEvent{" +
                "orderId='" + orderId + '\'' +
                ", symbol='" + symbol + '\'' +
                ", isBuy=" + isBuy +
                ", price=" + price +
                ", quantity=" + quantity +
                ", status=" + status +
                ", message='" + message + '\'' +
                ", timestamp=" + getTimestamp() +
                '}';
    }
}