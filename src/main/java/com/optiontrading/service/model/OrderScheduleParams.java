package com.optiontrading.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Parameters for scheduling an options order
 */
public class OrderScheduleParams {
    private final String indexSymbol; // nifty, sensex, etc.
    private final LocalDate expiryDate;
    private final double threshold; // for strike range calculation
    private final BigDecimal targetPremium;
    private final int lots;
    private final OrderType orderType; // BUY or SELL
    private final boolean hedgingEnabled;
    private final int hedgePointDifference;
    private final boolean stopLossEnabled;
    private final boolean moveSlToCost;
    private final boolean trailingSl;
    private final LocalDateTime executionTime;

    private OrderScheduleParams(Builder builder) {
        this.indexSymbol = builder.indexSymbol;
        this.expiryDate = builder.expiryDate;
        this.threshold = builder.threshold;
        this.targetPremium = builder.targetPremium;
        this.lots = builder.lots;
        this.orderType = builder.orderType;
        this.hedgingEnabled = builder.hedgingEnabled;
        this.hedgePointDifference = builder.hedgePointDifference;
        this.stopLossEnabled = builder.stopLossEnabled;
        this.moveSlToCost = builder.moveSlToCost;
        this.trailingSl = builder.trailingSl;
        this.executionTime = builder.executionTime;
    }

    public String getIndexSymbol() {
        return indexSymbol;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public double getThreshold() {
        return threshold;
    }

    public BigDecimal getTargetPremium() {
        return targetPremium;
    }

    public int getLots() {
        return lots;
    }

    public OrderType getOrderType() {
        return orderType;
    }

    public boolean isHedgingEnabled() {
        return hedgingEnabled;
    }

    public int getHedgePointDifference() {
        return hedgePointDifference;
    }

    public boolean isStopLossEnabled() {
        return stopLossEnabled;
    }

    public boolean isMoveSlToCost() {
        return moveSlToCost;
    }

    public boolean isTrailingSl() {
        return trailingSl;
    }

    public LocalDateTime getExecutionTime() {
        return executionTime;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public String toString() {
        return "OrderScheduleParams{" +
                "indexSymbol='" + indexSymbol + '\'' +
                ", expiryDate=" + expiryDate +
                ", threshold=" + threshold +
                ", targetPremium=" + targetPremium +
                ", lots=" + lots +
                ", orderType=" + orderType +
                ", hedgingEnabled=" + hedgingEnabled +
                ", hedgePointDifference=" + hedgePointDifference +
                ", stopLossEnabled=" + stopLossEnabled +
                ", executionTime=" + executionTime +
                '}';
    }

    public static class Builder {
        private String indexSymbol;
        private LocalDate expiryDate;
        private double threshold = 5.0; // default value
        private BigDecimal targetPremium;
        private int lots = 1; // default value
        private OrderType orderType = OrderType.BUY; // default value
        private boolean hedgingEnabled = false;
        private int hedgePointDifference = 0;
        private boolean stopLossEnabled = false;
        private boolean moveSlToCost = false;
        private boolean trailingSl = false;
        private LocalDateTime executionTime;

        public Builder indexSymbol(String indexSymbol) {
            this.indexSymbol = indexSymbol;
            return this;
        }

        public Builder expiryDate(LocalDate expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        public Builder threshold(double threshold) {
            this.threshold = threshold;
            return this;
        }

        public Builder targetPremium(BigDecimal targetPremium) {
            this.targetPremium = targetPremium;
            return this;
        }

        public Builder lots(int lots) {
            this.lots = lots;
            return this;
        }

        public Builder orderType(OrderType orderType) {
            this.orderType = orderType;
            return this;
        }

        public Builder hedgingEnabled(boolean hedgingEnabled) {
            this.hedgingEnabled = hedgingEnabled;
            return this;
        }

        public Builder hedgePointDifference(int hedgePointDifference) {
            this.hedgePointDifference = hedgePointDifference;
            return this;
        }

        public Builder stopLossEnabled(boolean stopLossEnabled) {
            this.stopLossEnabled = stopLossEnabled;
            return this;
        }

        public Builder moveSlToCost(boolean moveSlToCost) {
            this.moveSlToCost = moveSlToCost;
            return this;
        }

        public Builder trailingSl(boolean trailingSl) {
            this.trailingSl = trailingSl;
            return this;
        }

        public Builder executionTime(LocalDateTime executionTime) {
            this.executionTime = executionTime;
            return this;
        }

        public OrderScheduleParams build() {
            return new OrderScheduleParams(this);
        }
    }
}