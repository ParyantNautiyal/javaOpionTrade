package com.optiontrading.service.model;

import com.optiontrading.config.ConfigurationManager;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import com.optiontrading.service.position.StopLossType;

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
    private final BigDecimal stopLossPercentage; // Custom stop loss percentage
    private final BigDecimal trailingDistance; // Custom trailing distance
    private final StopLossType stopLossType; // Type of stop loss calculation
    private final StopLossType trailingType; // Type of trailing stop calculation
    private final LocalDateTime executionTime;

    // Configuration keys
    private static final String DEFAULT_STOP_LOSS_PERCENT_KEY = "trading.default.stop.loss.percentage";
    private static final String DEFAULT_HEDGE_POINT_DIFF_KEY = "trading.default.hedge.point.difference";
    private static final String DEFAULT_TRAILING_DISTANCE_KEY = "trading.default.trailing.distance";

    // Default values (used if configuration is not available)
    private static final double DEFAULT_THRESHOLD = 5.0;
    private static final int DEFAULT_HEDGE_POINT_DIFFERENCE = 1500;
    private static final double DEFAULT_STOP_LOSS_PERCENTAGE = 5.0;
    private static final double DEFAULT_TRAILING_DISTANCE = 0.5;

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
        this.stopLossPercentage = builder.stopLossPercentage;
        this.trailingDistance = builder.trailingDistance;
        this.stopLossType = builder.stopLossType;
        this.trailingType = builder.trailingType;
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

    public BigDecimal getStopLossPercentage() {
        return stopLossPercentage;
    }

    public BigDecimal getTrailingDistance() {
        return trailingDistance;
    }

    public StopLossType getStopLossType() {
        return stopLossType;
    }

    public StopLossType getTrailingType() {
        return trailingType;
    }

    public LocalDateTime getExecutionTime() {
        return executionTime;
    }

    public static Builder builder() {
        return new Builder();
    }

    /**
     * Create a builder with default values from configuration
     * 
     * @param configManager the configuration manager to get defaults from
     * @return a builder with defaults from configuration
     */
    public static Builder builderWithDefaults(ConfigurationManager configManager) {
        Builder builder = new Builder();

        if (configManager != null) {
            // Get default stop loss percentage from configuration
            double defaultStopLossPercent = configManager.getDouble(
                    DEFAULT_STOP_LOSS_PERCENT_KEY, DEFAULT_STOP_LOSS_PERCENTAGE);
            builder.threshold(defaultStopLossPercent);
            builder.stopLossPercentage(new BigDecimal(defaultStopLossPercent));

            // Get default hedge point difference from configuration
            int defaultHedgePointDiff = configManager.getInt(
                    DEFAULT_HEDGE_POINT_DIFF_KEY, DEFAULT_HEDGE_POINT_DIFFERENCE);
            builder.hedgePointDifference(defaultHedgePointDiff);

            // Get default trailing distance from configuration
            double defaultTrailingDistance = configManager.getDouble(
                    DEFAULT_TRAILING_DISTANCE_KEY, DEFAULT_TRAILING_DISTANCE);
            builder.trailingDistance(new BigDecimal(defaultTrailingDistance));
        }

        return builder;
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
                ", moveSlToCost=" + moveSlToCost +
                ", trailingSl=" + trailingSl +
                ", stopLossPercentage=" + stopLossPercentage +
                ", trailingDistance=" + trailingDistance +
                ", stopLossType=" + stopLossType +
                ", trailingType=" + trailingType +
                ", executionTime=" + executionTime +
                '}';
    }

    public static class Builder {
        private String indexSymbol;
        private LocalDate expiryDate;
        private double threshold = DEFAULT_THRESHOLD; // default value
        private BigDecimal targetPremium;
        private int lots = 1; // default value
        private OrderType orderType = OrderType.BUY; // default value
        private boolean hedgingEnabled = false;
        private int hedgePointDifference = DEFAULT_HEDGE_POINT_DIFFERENCE;
        private boolean stopLossEnabled = false;
        private boolean moveSlToCost = false;
        private boolean trailingSl = false;
        private BigDecimal stopLossPercentage = new BigDecimal(DEFAULT_STOP_LOSS_PERCENTAGE);
        private BigDecimal trailingDistance = new BigDecimal(DEFAULT_TRAILING_DISTANCE);
        private StopLossType stopLossType = StopLossType.PERCENTAGE; // Default to percentage
        private StopLossType trailingType = StopLossType.POINTS; // Default to points
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

        public Builder stopLossPercentage(BigDecimal stopLossPercentage) {
            this.stopLossPercentage = stopLossPercentage;
            return this;
        }

        public Builder trailingDistance(BigDecimal trailingDistance) {
            this.trailingDistance = trailingDistance;
            return this;
        }

        public Builder stopLossType(StopLossType stopLossType) {
            this.stopLossType = stopLossType;
            return this;
        }

        public Builder trailingType(StopLossType trailingType) {
            this.trailingType = trailingType;
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

    /**
     * Create a builder from an existing params object
     */
    public static Builder builderFrom(OrderScheduleParams params) {
        Builder builder = new Builder();

        if (params != null) {
            builder.indexSymbol(params.getIndexSymbol())
                    .expiryDate(params.getExpiryDate())
                    .threshold(params.getThreshold())
                    .targetPremium(params.getTargetPremium())
                    .lots(params.getLots())
                    .orderType(params.getOrderType())
                    .hedgingEnabled(params.isHedgingEnabled())
                    .hedgePointDifference(params.getHedgePointDifference())
                    .stopLossEnabled(params.isStopLossEnabled())
                    .moveSlToCost(params.isMoveSlToCost())
                    .trailingSl(params.isTrailingSl())
                    .stopLossPercentage(params.getStopLossPercentage())
                    .trailingDistance(params.getTrailingDistance())
                    .stopLossType(params.getStopLossType())
                    .trailingType(params.getTrailingType())
                    .executionTime(params.getExecutionTime());
        }

        return builder;
    }
}