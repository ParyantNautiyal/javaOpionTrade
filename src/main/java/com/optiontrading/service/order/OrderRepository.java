package com.optiontrading.service.order;

import com.optiontrading.events.EventBus;
import com.optiontrading.service.model.OrderScheduleParams;
import com.optiontrading.service.model.OrderStatus;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.model.Instrument;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
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
@Singleton
public class OrderRepository {
    private static final Logger LOGGER = Logger.getLogger(OrderRepository.class.getName());
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy");
    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");

    // Map of order ID to scheduled order
    private final Map<String, ScheduledOrder> orders = new ConcurrentHashMap<>();

    // Map of order ID to pre-filtered instruments (optimization)
    private final Map<String, List<Instrument>> preFilteredInstrumentsMap = new ConcurrentHashMap<>();

    // Event bus for publishing events
    private final EventBus eventBus;

    // Order logger service for detailed lifecycle logging
    private final OrderLoggerService orderLoggerService;

    /**
     * Constructor with dependency injection
     */
    @Inject
    public OrderRepository(EventBus eventBus, OrderLoggerService orderLoggerService) {
        this.eventBus = eventBus;
        this.orderLoggerService = orderLoggerService;
        LOGGER.info("Initialized OrderRepository with dependency injection");
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

        // Log order creation to dedicated file
        orderLoggerService.logOrderCreated(order);

        // Publish event
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
            LOGGER.warning("Cannot update status for non-existent order: " + orderId);
            return false;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);

        // Log status change with highly visible markers for important changes
        String logMessage = "Updated order " + orderId + " status: " + oldStatus + " -> " + newStatus;
        if (newStatus == OrderStatus.FAILED) {
            logMessage = "!!!ORDER FAILED!!! " + logMessage;
            LOGGER.severe(logMessage);
        } else if (newStatus == OrderStatus.COMPLETED) {
            logMessage = "***ORDER COMPLETED*** " + logMessage;
            LOGGER.info(logMessage);
        } else {
            LOGGER.info(logMessage);
        }

        try {
            // Force print to standard output as well for visibility
            System.out.println("[STATUS] " + logMessage);

            // Log to dedicated file through logger service
            orderLoggerService.logOrderStatusChange(orderId, oldStatus, newStatus);

            // Publish event for status change
            eventBus.publishAsync(new OrderStatusChangeEvent(orderId, oldStatus, newStatus));
            LOGGER.info("Published OrderStatusChangeEvent for order: " + orderId);

            return true;
        } catch (Exception e) {
            LOGGER.severe("Error publishing status change event: " + e.getMessage());
            e.printStackTrace();
            return false;
        }
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

        // Log order deletion to dedicated file
        orderLoggerService.logMessage(orderId, "ORDER DELETED - Order removed from the system");

        // Publish event
        eventBus.publishAsync(new OrderDeletedEvent(orderId));

        return true;
    }

    /**
     * Store pre-filtered instruments for an order
     * 
     * @param orderId     the order ID
     * @param instruments the pre-filtered instruments
     */
    public void storePreFilteredInstruments(String orderId, List<Instrument> instruments) {
        preFilteredInstrumentsMap.put(orderId, instruments);

        // Log to dedicated file
        if (instruments != null) {
            orderLoggerService.logMessage(orderId, "PRE-FILTERED INSTRUMENTS - Cached " +
                    instruments.size() + " instruments for efficient option selection");
        }
    }

    /**
     * Get pre-filtered instruments for an order
     * 
     * @param orderId the order ID
     * @return the pre-filtered instruments or null if not found
     */
    public List<Instrument> getPreFilteredInstruments(String orderId) {
        return preFilteredInstrumentsMap.get(orderId);
    }

    /**
     * Remove pre-filtered instruments for an order
     * 
     * @param orderId the order ID
     */
    public void clearPreFilteredInstruments(String orderId) {
        preFilteredInstrumentsMap.remove(orderId);
        String details = "Removed pre-filtered instruments cache";
        orderLoggerService.logMessage(orderId, "CLEANED UP - " + details);

        // Publish OrderCleanupEvent
        eventBus.publishAsync(new OrderCleanupEvent(orderId, details));
    }
}