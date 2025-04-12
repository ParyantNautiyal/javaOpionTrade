package com.optiontrading.service.position;

import com.optiontrading.events.Event;
import com.optiontrading.service.model.OrderType;

import java.math.BigDecimal;

/**
 * Event published when an order is executed for a position (trigger, stop loss,
 * or manual close)
 */
public class PositionOrderExecutedEvent extends Event {
    private final WatchedPosition position;
    private final OrderType executionOrderType;
    private final BigDecimal executionPrice;

    /**
     * Create a new position order executed event
     * 
     * @param position           the position that had an order executed
     * @param executionOrderType the type of order executed (BUY/SELL)
     * @param executionPrice     the price at which the order was executed
     */
    public PositionOrderExecutedEvent(WatchedPosition position, OrderType executionOrderType,
            BigDecimal executionPrice) {
        this.position = position;
        this.executionOrderType = executionOrderType;
        this.executionPrice = executionPrice;
    }

    /**
     * Get the position
     * 
     * @return the position
     */
    public WatchedPosition getPosition() {
        return position;
    }

    /**
     * Get the execution order type
     * 
     * @return the order type
     */
    public OrderType getExecutionOrderType() {
        return executionOrderType;
    }

    /**
     * Get the execution price
     * 
     * @return the execution price
     */
    public BigDecimal getExecutionPrice() {
        return executionPrice;
    }
}