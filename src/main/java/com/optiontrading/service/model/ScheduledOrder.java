package com.optiontrading.service.model;

import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * Represents an order scheduled for execution
 */
public class ScheduledOrder {
    private final String orderId;
    private final OrderScheduleParams params;
    private OrderStatus status;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public ScheduledOrder(OrderScheduleParams params) {
        this.orderId = UUID.randomUUID().toString();
        this.params = params;
        this.status = OrderStatus.SCHEDULED;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = this.createdAt;
    }

    public String getOrderId() {
        return orderId;
    }

    public OrderScheduleParams getParams() {
        return params;
    }

    public OrderStatus getStatus() {
        return status;
    }

    public void setStatus(OrderStatus status) {
        this.status = status;
        this.updatedAt = LocalDateTime.now();
    }

    public LocalDateTime getCreatedAt() {
        return createdAt;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDateTime getExecutionTime() {
        return params.getExecutionTime();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        ScheduledOrder that = (ScheduledOrder) o;
        return Objects.equals(orderId, that.orderId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(orderId);
    }

    @Override
    public String toString() {
        return "ScheduledOrder{" +
                "orderId='" + orderId + '\'' +
                ", status=" + status +
                ", executionTime=" + params.getExecutionTime() +
                ", index=" + params.getIndexSymbol() +
                ", expiry=" + params.getExpiryDate() +
                '}';
    }
}