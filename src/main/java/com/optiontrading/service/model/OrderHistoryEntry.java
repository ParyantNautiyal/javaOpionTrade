package com.optiontrading.service.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Represents a complete history entry for an order, suitable for JSON
 * serialization.
 */
public class OrderHistoryEntry {
    private String orderId;
    private String indexSymbol;
    private OrderType orderType;
    private int lots;
    private LocalDateTime createdAt;
    private LocalDateTime executionTime;
    private OrderStatus status;
    private String expiryDate;
    private BigDecimal targetPremium;
    private boolean stopLossEnabled;
    private BigDecimal threshold;
    private boolean moveSlToCost;
    private boolean trailingSl;
    private boolean hedgingEnabled;
    private BigDecimal hedgePointDifference;
    private List<OrderEvent> events = new ArrayList<>();
    private String callOptionSymbol;
    private String putOptionSymbol;
    private BigDecimal callPrice;
    private BigDecimal putPrice;
    private BigDecimal combinedPremium;
    private String failureReason;
    private String notes;

    // Event subclass to track order lifecycle
    public static class OrderEvent {
        private LocalDateTime timestamp;
        private String eventType;
        private String description;

        public OrderEvent() {
        }

        public OrderEvent(LocalDateTime timestamp, String eventType, String description) {
            this.timestamp = timestamp;
            this.eventType = eventType;
            this.description = description;
        }

        // Getters and setters
        public LocalDateTime getTimestamp() {
            return timestamp;
        }

        public void setTimestamp(LocalDateTime timestamp) {
            this.timestamp = timestamp;
        }

        public String getEventType() {
            return eventType;
        }

        public void setEventType(String eventType) {
            this.eventType = eventType;
        }

        public String getDescription() {
            return description;
        }

        public void setDescription(String description) {
            this.description = description;
        }
    }

    // Constructors
    public OrderHistoryEntry() {
    }

    public OrderHistoryEntry(String orderId) {
        this.orderId = orderId;
        this.createdAt = LocalDateTime.now();
    }

    // Add an event to the history
    public void addEvent(String eventType, String description) {
        events.add(new OrderEvent(LocalDateTime.now(), eventType, description));
    }

    // Getters and setters
    public String getOrderId() {
        return orderId;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public String getIndexSymbol() {
        return indexSymbol;
    }

    public void setIndexSymbol(String indexSymbol) {
        this.indexSymbol = indexSymbol;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public void setOrderType(OrderType orderType) {
        this.orderType = orderType;
    }

    public int getLots() {
        return lots;
    }

    public void setLots(int lots) {
        this.lots = lots;
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public LocalDateTime getExecutionTime() {
        return executionTime;
    }

    public void setExecutionTime(LocalDateTime executionTime) {
        this.executionTime = executionTime;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
    }

    public String getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(String expiryDate) {
        this.expiryDate = expiryDate;
    }

    public BigDecimal getTargetPremium() {
        return targetPremium;
    }

    public void setTargetPremium(BigDecimal targetPremium) {
        this.targetPremium = targetPremium;
    }

    public boolean isStopLossEnabled() {
        return stopLossEnabled;
    }

    public void setStopLossEnabled(boolean stopLossEnabled) {
        this.stopLossEnabled = stopLossEnabled;
    }

    public BigDecimal getThreshold() {
        return threshold;
    }

    public void setThreshold(BigDecimal threshold) {
        this.threshold = threshold;
    }

    public boolean isMoveSlToCost() {
        return moveSlToCost;
    }

    public void setMoveSlToCost(boolean moveSlToCost) {
        this.moveSlToCost = moveSlToCost;
    }

    public boolean isTrailingSl() {
        return trailingSl;
    }

    public void setTrailingSl(boolean trailingSl) {
        this.trailingSl = trailingSl;
    }

    public boolean isHedgingEnabled() {
        return hedgingEnabled;
    }

    public void setHedgingEnabled(boolean hedgingEnabled) {
        this.hedgingEnabled = hedgingEnabled;
    }

    public BigDecimal getHedgePointDifference() {
        return hedgePointDifference;
    }

    public void setHedgePointDifference(BigDecimal hedgePointDifference) {
        this.hedgePointDifference = hedgePointDifference;
    }

    public List<OrderEvent> getEvents() {
        return events;
    }

    public void setEvents(List<OrderEvent> events) {
        this.events = events;
    }

    public String getCallOptionSymbol() {
        return callOptionSymbol;
    }

    public void setCallOptionSymbol(String callOptionSymbol) {
        this.callOptionSymbol = callOptionSymbol;
    }

    public String getPutOptionSymbol() {
        return putOptionSymbol;
    }

    public void setPutOptionSymbol(String putOptionSymbol) {
        this.putOptionSymbol = putOptionSymbol;
    }

    public BigDecimal getCallPrice() {
        return callPrice;
    }

    public void setCallPrice(BigDecimal callPrice) {
        this.callPrice = callPrice;
    }

    public BigDecimal getPutPrice() {
        return putPrice;
    }

    public void setPutPrice(BigDecimal putPrice) {
        this.putPrice = putPrice;
    }

    public BigDecimal getCombinedPremium() {
        return combinedPremium;
    }

    public void setCombinedPremium(BigDecimal combinedPremium) {
        this.combinedPremium = combinedPremium;
    }

    public String getFailureReason() {
        return failureReason;
    }

    public void setFailureReason(String failureReason) {
        this.failureReason = failureReason;
    }

    public String getNotes() {
        return notes;
    }

    public void setNotes(String notes) {
        this.notes = notes;
    }
}