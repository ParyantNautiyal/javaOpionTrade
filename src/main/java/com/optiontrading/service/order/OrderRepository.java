package com.optiontrading.service.order;

import com.optiontrading.events.EventBus;
import com.optiontrading.service.model.OrderScheduleParams;
import com.optiontrading.service.model.OrderStatus;
import com.optiontrading.service.model.ScheduledOrder;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Repository for storing and retrieving scheduled orders
 */
public class OrderRepository {
    private static final Logger LOGGER = Logger.getLogger(OrderRepository.class.getName());
    private static final OrderRepository INSTANCE = new OrderRepository();

    // Map of order ID to scheduled order
    private final Map<String, ScheduledOrder> orders = new ConcurrentHashMap<>();

    // Event bus for publishing events
    private final EventBus eventBus;

    // Private constructor for singleton
    private OrderRepository() {
        this.eventBus = EventBus.getInstance();
        LOGGER.info("Initialized OrderRepository");
    }

    /**
     * Get the singleton instance
     */
    public static OrderRepository getInstance() {
        return INSTANCE;
    }

    /**
     * Create a new scheduled order
     * 
     * @param params the order parameters
     * @return the created order
     */
    public ScheduledOrder createOrder(OrderScheduleParams params) {
        if (params == null) {
            throw new IllegalArgumentException("Order parameters cannot be null");
        }

        ScheduledOrder order = new ScheduledOrder(params);
        orders.put(order.getOrderId(), order);

        LOGGER.info("Created scheduled order: " + order);

        // Publish event (you would create this class)
        eventBus.publishAsync(new OrderCreatedEvent(order));

        return order;
    }

    /**
     * Get an order by ID
     * 
     * @param orderId the order ID
     * @return the order, or null if not found
     */
    public ScheduledOrder getOrder(String orderId) {
        return orders.get(orderId);
    }

    /**
     * Get all orders
     * 
     * @return list of all orders
     */
    public List<ScheduledOrder> getAllOrders() {
        return new ArrayList<>(orders.values());
    }

    /**
     * Get orders scheduled to execute within a time range
     * 
     * @param from start of time range (inclusive)
     * @param to   end of time range (exclusive)
     * @return list of orders scheduled in the range
     */
    public List<ScheduledOrder> getOrdersToExecute(LocalDateTime from, LocalDateTime to) {
        if (from == null || to == null || from.isAfter(to)) {
            return Collections.emptyList();
        }

        return orders.values().stream()
                .filter(order -> order.getStatus() == OrderStatus.SCHEDULED)
                .filter(order -> {
                    LocalDateTime executionTime = order.getExecutionTime();
                    return executionTime != null &&
                            !executionTime.isBefore(from) &&
                            executionTime.isBefore(to);
                })
                .collect(Collectors.toList());
    }

    /**
     * Update the status of an order
     * 
     * @param orderId   the order ID
     * @param newStatus the new status
     * @return true if the order was updated, false if not found
     */
    public boolean updateOrderStatus(String orderId, OrderStatus newStatus) {
        ScheduledOrder order = orders.get(orderId);
        if (order == null) {
            return false;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);

        LOGGER.info("Updated order " + orderId + " status: " + oldStatus + " -> " + newStatus);

        // Publish event (you would create this class)
        eventBus.publishAsync(new OrderStatusChangedEvent(orderId, oldStatus, newStatus));

        return true;
    }

    /**
     * Delete an order
     * 
     * @param orderId the order ID
     * @return true if the order was deleted, false if not found
     */
    public boolean deleteOrder(String orderId) {
        ScheduledOrder order = orders.remove(orderId);
        if (order == null) {
            return false;
        }

        LOGGER.info("Deleted order: " + order);

        // Publish event (you would create this class)
        eventBus.publishAsync(new OrderDeletedEvent(orderId));

        return true;
    }
}