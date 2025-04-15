package com.optiontrading.ui;

import com.optiontrading.service.position.WatchedPosition;
import javafx.beans.property.*;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;

/**
 * View model class for displaying positions in the UI
 */
public class PositionViewModel {
    private final StringProperty symbol = new SimpleStringProperty();
    private final StringProperty type = new SimpleStringProperty();
    private final StringProperty quantity = new SimpleStringProperty();
    private final StringProperty entryPrice = new SimpleStringProperty();
    private final StringProperty entryTime = new SimpleStringProperty();
    private final StringProperty currentPrice = new SimpleStringProperty();
    private final StringProperty pnlAmount = new SimpleStringProperty();
    private final StringProperty pnlPercent = new SimpleStringProperty();
    private final StringProperty stopLoss = new SimpleStringProperty();
    private final StringProperty target = new SimpleStringProperty();
    private final StringProperty status = new SimpleStringProperty();

    // Original data for reference/updates
    private WatchedPosition position;

    private static final DecimalFormat PRICE_FORMAT = new DecimalFormat("#,##0.00");
    private static final DecimalFormat PERCENT_FORMAT = new DecimalFormat("0.00%");
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM HH:mm:ss");

    /**
     * Create a view model from a WatchedPosition
     */
    public PositionViewModel(WatchedPosition position) {
        this.position = position;
        updateFromPosition(position);
    }

    /**
     * Update this view model from a position
     */
    public void updateFromPosition(WatchedPosition position) {
        if (position == null)
            return;

        this.position = position;

        // Basic information
        symbol.set(position.getInstrument().getTradingSymbol());
        type.set(position.isLongPosition() ? "LONG" : "SHORT");
        quantity.set(String.valueOf(position.getQuantity()));

        // Entry details
        entryPrice.set(formatPrice(position.getEntryPrice()));
        entryTime.set(position.getEntryTime().format(DATE_TIME_FORMATTER));

        // Current price
        currentPrice.set(formatPrice(position.getCurrentPrice()));

        // P&L
        boolean isProfit = position.getPnl().compareTo(BigDecimal.ZERO) > 0;
        pnlAmount.set((isProfit ? "+" : "") + formatPrice(position.getPnl()));
        pnlPercent.set((isProfit ? "+" : "") + formatPercent(position.getPnlPercent()));

        // Risk management
        stopLoss.set(formatPrice(position.getStopLoss()));
        target.set(formatPrice(position.getTarget()));

        // Status display
        setStatusDisplay(position);
    }

    private void setStatusDisplay(WatchedPosition position) {
        StringBuilder statusText = new StringBuilder();

        if (position.isMoveToBreakeven() && position.isStopLossMovedToCost()) {
            statusText.append("SL@Cost ");
        }

        if (position.isTrailingSlEnabled()) {
            statusText.append("Trailing ");
        }

        // Check how close we are to stop loss
        BigDecimal slDistance = position.getDistanceToStopLoss();
        if (slDistance != null) {
            BigDecimal slPercentage = slDistance.abs()
                    .divide(position.getCurrentPrice(), 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal.valueOf(100));

            if (slPercentage.compareTo(BigDecimal.valueOf(1.0)) <= 0) {
                statusText.append("NEAR SL! ");
            }
        }

        if (statusText.length() == 0) {
            statusText.append("Active");
        }

        status.set(statusText.toString().trim());
    }

    private String formatPrice(BigDecimal value) {
        if (value == null)
            return "N/A";
        return PRICE_FORMAT.format(value);
    }

    private String formatPercent(BigDecimal value) {
        if (value == null)
            return "N/A";
        // Convert decimal to percentage (e.g., 0.05 to 5%)
        return PERCENT_FORMAT.format(value.divide(BigDecimal.valueOf(100), 4, RoundingMode.HALF_UP));
    }

    // Getters for JavaFX properties
    public StringProperty symbolProperty() {
        return symbol;
    }

    public StringProperty typeProperty() {
        return type;
    }

    public StringProperty quantityProperty() {
        return quantity;
    }

    public StringProperty entryPriceProperty() {
        return entryPrice;
    }

    public StringProperty entryTimeProperty() {
        return entryTime;
    }

    public StringProperty currentPriceProperty() {
        return currentPrice;
    }

    public StringProperty pnlAmountProperty() {
        return pnlAmount;
    }

    public StringProperty pnlPercentProperty() {
        return pnlPercent;
    }

    public StringProperty stopLossProperty() {
        return stopLoss;
    }

    public StringProperty targetProperty() {
        return target;
    }

    public StringProperty statusProperty() {
        return status;
    }

    // Get the underlying position
    public WatchedPosition getPosition() {
        return position;
    }

    // For tableview row identifiers
    public String getId() {
        return position.getId();
    }
}