package com.optiontrading.service.order;

import com.optiontrading.events.EventBus;
import com.optiontrading.logging.OrderLogger;
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

    /**
     * Constructor with dependency injection
     */
    @Inject
    public OrderRepository(EventBus eventBus) {
        this.eventBus = eventBus;
        OrderLogger.initialize();
        LOGGER.info("Initialized OrderRepository with dependency injection");
        OrderLogger.info("OrderRepository initialized and ready for order management");
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

        // Log detailed order creation with dedicated logger
        OrderLogger.logOrderCreation(
                order.getOrderId(),
                params.getIndexSymbol(),
                params.getExpiryDate().format(DATE_FORMATTER),
                params.getOrderType().toString(),
                params.getExecutionTime().format(TIME_FORMATTER));

        // Log specific details about the order parameters
        OrderLogger.detail("ORDER PARAMETERS: " + order.getOrderId() +
                "\n    Threshold: " + params.getThreshold() +
                "\n    Premium: " + params.getTargetPremium() +
                "\n    Lots: " + params.getLots() +
                "\n    StopLoss: " + params.isStopLossEnabled() +
                "\n    Hedging: " + params.isHedgingEnabled() +
                (params.isHedgingEnabled() ? "\n    HedgePointDiff: " + params.getHedgePointDifference() : ""));

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

        OrderLogger.detail("CHECKING ORDERS in time range: " + from.format(TIME_FORMATTER) +
                " to " + to.format(TIME_FORMATTER));

        List<ScheduledOrder> result = orders.values().stream()
                .filter(order -> order.getStatus() == OrderStatus.SCHEDULED)
                .filter(order -> {
                    LocalDateTime executionTime = order.getExecutionTime();
                    return executionTime != null &&
                            !executionTime.isBefore(from) &&
                            executionTime.isBefore(to);
                })
                .collect(Collectors.toList());

        if (!result.isEmpty()) {
            OrderLogger.info("FOUND " + result.size() + " orders to execute in time range");
            for (ScheduledOrder order : result) {
                OrderLogger.detail("ORDER SCHEDULED FOR EXECUTION: " + order.getOrderId() +
                        ", Time: " + order.getExecutionTime().format(TIME_FORMATTER));
            }
        } else {
            OrderLogger.detail("NO ORDERS found for execution in time range");
        }

        return result;
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
            OrderLogger.warning("Cannot update status for non-existent order: " + orderId);
            return false;
        }

        OrderStatus oldStatus = order.getStatus();
        order.setStatus(newStatus);

        LOGGER.info("Updated order " + orderId + " status: " + oldStatus + " -> " + newStatus);
        OrderLogger.logStatusChange(orderId, oldStatus.toString(), newStatus.toString());

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
        OrderLogger.info("ORDER DELETED: " + orderId);

        // Publish event (you would create this class)
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
        OrderLogger.logInstrumentFiltering(
                orderId,
                getOrder(orderId).getParams().getIndexSymbol(),
                getOrder(orderId).getParams().getExpiryDate().format(DATE_FORMATTER),
                instruments.size());
    }

    /**
     * Get pre-filtered instruments for an order
     * 
     * @param orderId the order ID
     * @return the pre-filtered instruments or null if not found
     */
    public List<Instrument> getPreFilteredInstruments(String orderId) {
        List<Instrument> instruments = preFilteredInstrumentsMap.get(orderId);
        if (instruments != null) {
            OrderLogger.detail("RETRIEVED " + instruments.size() + " pre-filtered instruments for order: " + orderId);
        }
        return instruments;
    }

    /**
     * Remove pre-filtered instruments for an order
     * 
     * @param orderId the order ID
     */
    public void clearPreFilteredInstruments(String orderId) {
        preFilteredInstrumentsMap.remove(orderId);
        OrderLogger.detail("CLEARED pre-filtered instruments for order: " + orderId);
    }
}