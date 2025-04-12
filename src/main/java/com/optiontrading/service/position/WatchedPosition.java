package com.optiontrading.service.position;

import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OrderType;

import java.io.Serializable;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.UUID;

/**
 * Represents a position that is being monitored for price-based conditions
 */
public class WatchedPosition implements Serializable {
    private static final long serialVersionUID = 1L;

    // Unique identifier for this position
    private final String id;

    // Basic position information
    private final Instrument instrument;
    private final OrderType orderType;
    private final int quantity;
    private final BigDecimal entryPrice;
    private final LocalDateTime entryTime;

    // Source of this position
    private final PositionSource source;

    // For simple price trigger positions (MANUAL)
    private BigDecimal triggerPrice;

    // For stop-loss positions (STRATEGY)
    private BigDecimal stopLossPercentage;
    private boolean moveToBreakeven;
    private boolean trailingStopLoss;
    private BigDecimal trailingDistance;

    // Dynamic values that change during monitoring
    private BigDecimal currentStopPrice;
    private BigDecimal highestSeen; // For trailing stops on BUY positions
    private BigDecimal lowestSeen; // For trailing stops on SELL positions
    private String brokerId; // ID of the broker order if executed

    // Position status
    private PositionStatus status;
    private String statusReason;
    private LocalDateTime statusChangeTime;

    private final double entryPriceDouble;
    private final int quantityInt;
    private final LocalDateTime addedTime;
    private double currentPrice;
    private double profitLoss;

    private WatchedPosition(Builder builder) {
        this.id = builder.id != null ? builder.id : UUID.randomUUID().toString();
        this.instrument = builder.instrument;
        this.orderType = builder.orderType;
        this.quantity = builder.quantity;
        this.entryPrice = builder.entryPrice;
        this.entryTime = builder.entryTime != null ? builder.entryTime : LocalDateTime.now();
        this.source = builder.source;
        this.triggerPrice = builder.triggerPrice;
        this.stopLossPercentage = builder.stopLossPercentage;
        this.moveToBreakeven = builder.moveToBreakeven;
        this.trailingStopLoss = builder.trailingStopLoss;
        this.trailingDistance = builder.trailingDistance;
        this.currentStopPrice = builder.currentStopPrice;
        this.highestSeen = builder.highestSeen != null ? builder.highestSeen : entryPrice;
        this.lowestSeen = builder.lowestSeen != null ? builder.lowestSeen : entryPrice;
        this.brokerId = builder.brokerId;
        this.status = builder.status != null ? builder.status : PositionStatus.ACTIVE;
        this.statusReason = builder.statusReason;
        this.statusChangeTime = builder.statusChangeTime != null ? builder.statusChangeTime : LocalDateTime.now();
        this.entryPriceDouble = entryPrice.doubleValue();
        this.quantityInt = quantity;
        this.addedTime = builder.addedTime != null ? builder.addedTime : LocalDateTime.now();
        this.currentPrice = entryPrice.doubleValue();
        this.profitLoss = 0.0;
    }

    /**
     * Check if this is a simple price trigger position
     * 
     * @return true if this is a price trigger position
     */
    public boolean isPriceTrigger() {
        return source == PositionSource.MANUAL && triggerPrice != null;
    }

    /**
     * Check if this is a stop-loss monitored position
     * 
     * @return true if this is a stop-loss position
     */
    public boolean isStopLoss() {
        return source == PositionSource.STRATEGY && stopLossPercentage != null;
    }

    /**
     * Update the position status with reason and timestamp
     * 
     * @param newStatus the new status
     * @param reason    the reason for the status change
     * @return true if the status was changed
     */
    public boolean updateStatus(PositionStatus newStatus, String reason) {
        if (this.status != newStatus) {
            this.status = newStatus;
            this.statusReason = reason;
            this.statusChangeTime = LocalDateTime.now();
            return true;
        }
        return false;
    }

    /**
     * Update the highest price seen for trailing stops
     * 
     * @param price the new price
     * @return true if the highest price was updated
     */
    public boolean updateHighestPrice(BigDecimal price) {
        if (orderType == OrderType.BUY && price.compareTo(highestSeen) > 0) {
            this.highestSeen = price;
            return true;
        }
        return false;
    }

    /**
     * Update the lowest price seen for trailing stops
     * 
     * @param price the new price
     * @return true if the lowest price was updated
     */
    public boolean updateLowestPrice(BigDecimal price) {
        if (orderType == OrderType.SELL && price.compareTo(lowestSeen) < 0) {
            this.lowestSeen = price;
            return true;
        }
        return false;
    }

    /**
     * Get the id
     * 
     * @return the id
     */
    public String getId() {
        return id;
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
     * Get the order type
     * 
     * @return the order type
     */
    public OrderType getOrderType() {
        return orderType;
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
     * Get the entry price
     * 
     * @return the entry price
     */
    public BigDecimal getEntryPrice() {
        return entryPrice;
    }

    /**
     * Get the entry time
     * 
     * @return the entry time
     */
    public LocalDateTime getEntryTime() {
        return entryTime;
    }

    /**
     * Get the source
     * 
     * @return the source
     */
    public PositionSource getSource() {
        return source;
    }

    /**
     * Get the trigger price
     * 
     * @return the trigger price
     */
    public BigDecimal getTriggerPrice() {
        return triggerPrice;
    }

    /**
     * Get the stop loss percentage
     * 
     * @return the stop loss percentage
     */
    public BigDecimal getStopLossPercentage() {
        return stopLossPercentage;
    }

    /**
     * Check if move to breakeven is enabled
     * 
     * @return true if move to breakeven is enabled
     */
    public boolean isMoveToBreakeven() {
        return moveToBreakeven;
    }

    /**
     * Check if trailing stop loss is enabled
     * 
     * @return true if trailing stop loss is enabled
     */
    public boolean isTrailingStopLoss() {
        return trailingStopLoss;
    }

    /**
     * Get the trailing distance
     * 
     * @return the trailing distance
     */
    public BigDecimal getTrailingDistance() {
        return trailingDistance;
    }

    /**
     * Get the current stop price
     * 
     * @return the current stop price
     */
    public BigDecimal getCurrentStopPrice() {
        return currentStopPrice;
    }

    /**
     * Set the current stop price
     * 
     * @param currentStopPrice the current stop price
     */
    public void setCurrentStopPrice(BigDecimal currentStopPrice) {
        this.currentStopPrice = currentStopPrice;
    }

    /**
     * Get the highest price seen
     * 
     * @return the highest price seen
     */
    public BigDecimal getHighestSeen() {
        return highestSeen;
    }

    /**
     * Get the lowest price seen
     * 
     * @return the lowest price seen
     */
    public BigDecimal getLowestSeen() {
        return lowestSeen;
    }

    /**
     * Get the broker order ID
     * 
     * @return the broker order ID
     */
    public String getBrokerId() {
        return brokerId;
    }

    /**
     * Set the broker order ID
     * 
     * @param brokerId the broker order ID
     */
    public void setBrokerId(String brokerId) {
        this.brokerId = brokerId;
    }

    /**
     * Get the status
     * 
     * @return the status
     */
    public PositionStatus getStatus() {
        return status;
    }

    /**
     * Get the status reason
     * 
     * @return the status reason
     */
    public String getStatusReason() {
        return statusReason;
    }

    /**
     * Get the status change time
     * 
     * @return the status change time
     */
    public LocalDateTime getStatusChangeTime() {
        return statusChangeTime;
    }

    /**
     * Check if the position is active
     * 
     * @return true if the position is active
     */
    public boolean isActive() {
        return status == PositionStatus.ACTIVE;
    }

    /**
     * Updates the current price and recalculates P&L
     * 
     * @param newPrice the new market price
     */
    public void updatePrice(double newPrice) {
        this.currentPrice = newPrice;
        calculateProfitLoss();
    }

    /**
     * Calculates the current profit or loss for this position
     */
    private void calculateProfitLoss() {
        this.profitLoss = (currentPrice - entryPriceDouble) * quantityInt;
    }

    /**
     * Get the added time
     * 
     * @return the added time
     */
    public LocalDateTime getAddedTime() {
        return addedTime;
    }

    /**
     * Get the current price
     * 
     * @return the current price
     */
    public double getCurrentPrice() {
        return currentPrice;
    }

    /**
     * Get the profit or loss
     * 
     * @return the profit or loss
     */
    public double getProfitLoss() {
        return profitLoss;
    }

    /**
     * Return the type of position (LONG or SHORT)
     * 
     * @return position type as string
     */
    public String getPositionType() {
        return quantity > 0 ? "LONG" : "SHORT";
    }

    /**
     * Builder for WatchedPosition
     */
    public static class Builder {
        private String id;
        private Instrument instrument;
        private OrderType orderType;
        private int quantity;
        private BigDecimal entryPrice;
        private LocalDateTime entryTime;
        private PositionSource source;
        private BigDecimal triggerPrice;
        private BigDecimal stopLossPercentage;
        private boolean moveToBreakeven;
        private boolean trailingStopLoss;
        private BigDecimal trailingDistance;
        private BigDecimal currentStopPrice;
        private BigDecimal highestSeen;
        private BigDecimal lowestSeen;
        private String brokerId;
        private PositionStatus status;
        private String statusReason;
        private LocalDateTime statusChangeTime;
        private LocalDateTime addedTime;

        public Builder id(String id) {
            this.id = id;
            return this;
        }

        public Builder instrument(Instrument instrument) {
            this.instrument = instrument;
            return this;
        }

        public Builder orderType(OrderType orderType) {
            this.orderType = orderType;
            return this;
        }

        public Builder quantity(int quantity) {
            this.quantity = quantity;
            return this;
        }

        public Builder entryPrice(BigDecimal entryPrice) {
            this.entryPrice = entryPrice;
            return this;
        }

        public Builder entryTime(LocalDateTime entryTime) {
            this.entryTime = entryTime;
            return this;
        }

        public Builder source(PositionSource source) {
            this.source = source;
            return this;
        }

        public Builder triggerPrice(BigDecimal triggerPrice) {
            this.triggerPrice = triggerPrice;
            return this;
        }

        public Builder stopLossPercentage(BigDecimal stopLossPercentage) {
            this.stopLossPercentage = stopLossPercentage;
            return this;
        }

        public Builder moveToBreakeven(boolean moveToBreakeven) {
            this.moveToBreakeven = moveToBreakeven;
            return this;
        }

        public Builder trailingStopLoss(boolean trailingStopLoss) {
            this.trailingStopLoss = trailingStopLoss;
            return this;
        }

        public Builder trailingDistance(BigDecimal trailingDistance) {
            this.trailingDistance = trailingDistance;
            return this;
        }

        public Builder currentStopPrice(BigDecimal currentStopPrice) {
            this.currentStopPrice = currentStopPrice;
            return this;
        }

        public Builder highestSeen(BigDecimal highestSeen) {
            this.highestSeen = highestSeen;
            return this;
        }

        public Builder lowestSeen(BigDecimal lowestSeen) {
            this.lowestSeen = lowestSeen;
            return this;
        }

        public Builder brokerId(String brokerId) {
            this.brokerId = brokerId;
            return this;
        }

        public Builder status(PositionStatus status) {
            this.status = status;
            return this;
        }

        public Builder statusReason(String statusReason) {
            this.statusReason = statusReason;
            return this;
        }

        public Builder statusChangeTime(LocalDateTime statusChangeTime) {
            this.statusChangeTime = statusChangeTime;
            return this;
        }

        public Builder addedTime(LocalDateTime addedTime) {
            this.addedTime = addedTime;
            return this;
        }

        public WatchedPosition build() {
            return new WatchedPosition(this);
        }
    }

    /**
     * Create a new builder
     * 
     * @return a new builder
     */
    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "WatchedPosition{" +
                "id='" + id + '\'' +
                ", instrument=" + (instrument != null ? instrument.getTradingSymbol() : "null") +
                ", orderType=" + orderType +
                ", status=" + status +
                ", source=" + source +
                '}';
    }
}