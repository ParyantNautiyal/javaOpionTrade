package com.optiontrading.service.order;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.service.model.OptionPair;
import com.optiontrading.service.model.OrderHistoryEntry;
import com.optiontrading.service.model.OrderStatus;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.option.BestOptionsUpdatedEvent;
import com.optiontrading.service.trading.OrderFailedEvent;
import com.optiontrading.service.trading.OrderPlacedEvent;
import com.optiontrading.service.model.OrderType;
import com.optiontrading.service.model.Instrument;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Logger;

/**
 * Service for storing order history in JSON format
 */
@Singleton
public class OrderLoggerService {
    private static final Logger LOGGER = Logger.getLogger(OrderLoggerService.class.getName());
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    // Map to store order history in memory before saving to JSON
    private final ConcurrentHashMap<String, OrderHistoryEntry> orderHistoryMap = new ConcurrentHashMap<>();
    private final OrderHistoryRepository orderHistoryRepository;

    /**
     * Constructor with dependency injection
     */
    @Inject
    public OrderLoggerService(EventBus eventBus, OrderHistoryRepository orderHistoryRepository) {
        this.orderHistoryRepository = orderHistoryRepository;

        // Subscribe to order-related events
        subscribeToEvents(eventBus);

        LOGGER.info("Order logger initialized - JSON-only mode enabled");
    }

    /**
     * Subscribe to order-related events
     */
    private void subscribeToEvents(EventBus eventBus) {
        // Order Created Event
        eventBus.subscribe(OrderCreatedEvent.class, new EventSubscriber<OrderCreatedEvent>() {
            @Override
            public void onEvent(OrderCreatedEvent event) {
                logOrderCreated(event.getOrder());
            }
        });

        // Best Options Updated Event
        eventBus.subscribe(BestOptionsUpdatedEvent.class, new EventSubscriber<BestOptionsUpdatedEvent>() {
            @Override
            public void onEvent(BestOptionsUpdatedEvent event) {
                logBestOptionsSelected(event.getOrderId(), event.getOptionPair());
            }
        });

        // Order placed events
        eventBus.subscribe(OrderPlacedEvent.class, new EventSubscriber<OrderPlacedEvent>() {
            @Override
            public void onEvent(OrderPlacedEvent event) {
                logOrderPlaced(event);
            }
        });

        // Order failed events
        eventBus.subscribe(OrderFailedEvent.class, new EventSubscriber<OrderFailedEvent>() {
            @Override
            public void onEvent(OrderFailedEvent event) {
                logOrderFailed(event);
            }
        });

        // Hedge Orders Placed Event
        eventBus.subscribe(HedgeOrdersPlacedEvent.class, new EventSubscriber<HedgeOrdersPlacedEvent>() {
            @Override
            public void onEvent(HedgeOrdersPlacedEvent event) {
                logHedgeOrdersPlaced(event.getOrderId(), event.getOptionPair());
            }
        });

        // Main Order Placed Event
        eventBus.subscribe(MainOrderPlacedEvent.class, new EventSubscriber<MainOrderPlacedEvent>() {
            @Override
            public void onEvent(MainOrderPlacedEvent event) {
                logMainOrderPlaced(event.getOrderId(), event.getOptionPair());
            }
        });

        // Order Cleanup Event
        eventBus.subscribe(OrderCleanupEvent.class, new EventSubscriber<OrderCleanupEvent>() {
            @Override
            public void onEvent(OrderCleanupEvent event) {
                logOrderCleanup(event.getOrderId(), event.getDetails());
            }
        });

        // Order Status Change Event
        eventBus.subscribe(OrderStatusChangeEvent.class, new EventSubscriber<OrderStatusChangeEvent>() {
            @Override
            public void onEvent(OrderStatusChangeEvent event) {
                logOrderStatusChange(event.getOrderId(), event.getOldStatus(), event.getNewStatus());
            }
        });
    }

    /**
     * Helper method to calculate combined premium for an option pair
     */
    private BigDecimal getCombinedPremium(OptionPair optionPair) {
        if (optionPair == null) {
            return BigDecimal.ZERO;
        }

        BigDecimal callPremium = optionPair.hasCallOption() ? optionPair.getCallPrice() : BigDecimal.ZERO;
        BigDecimal putPremium = optionPair.hasPutOption() ? optionPair.getPutPrice() : BigDecimal.ZERO;

        return callPremium.add(putPremium);
    }

    /**
     * Helper to get or create an order history entry
     */
    private OrderHistoryEntry getOrCreateHistoryEntry(String orderId) {
        OrderHistoryEntry entry = orderHistoryRepository.getOrderHistory(orderId);
        if (entry == null) {
            entry = new OrderHistoryEntry(orderId);
        }
        return entry;
    }

    /**
     * Log an order creation
     */
    public void logOrderCreated(ScheduledOrder order) {
        String orderId = order.getOrderId();
        LOGGER.info(String.format(
                "ORDER CREATED - ID: %s, Index: %s, Type: %s, Lots: %d, Execution Time: %s",
                orderId,
                order.getParams().getIndexSymbol(),
                order.getParams().getOrderType(),
                order.getParams().getLots(),
                formatDateTime(order.getExecutionTime())));

        // Create JSON history entry
        OrderHistoryEntry entry = new OrderHistoryEntry(orderId);
        entry.setIndexSymbol(order.getParams().getIndexSymbol());
        entry.setOrderType(order.getParams().getOrderType());
        entry.setLots(order.getParams().getLots());
        entry.setCreatedAt(order.getCreatedAt());
        entry.setExecutionTime(order.getExecutionTime());

        // Convert enums and other types properly
        entry.setStatus(OrderStatus.SCHEDULED); // Use SCHEDULED instead of PENDING

        // Handle expiry date conversion - assuming expiryDate is String in
        // OrderHistoryEntry
        entry.setExpiryDate(order.getParams().getExpiryDate().toString());

        // Convert target premium to BigDecimal if needed
        entry.setTargetPremium(order.getParams().getTargetPremium());

        entry.setStopLossEnabled(order.getParams().isStopLossEnabled());

        // Convert threshold to BigDecimal
        entry.setThreshold(BigDecimal.valueOf(order.getParams().getThreshold()));

        entry.setMoveSlToCost(order.getParams().isMoveSlToCost());
        entry.setTrailingSl(order.getParams().isTrailingSl());
        entry.setHedgingEnabled(order.getParams().isHedgingEnabled());

        // Convert hedge point difference to BigDecimal
        entry.setHedgePointDifference(BigDecimal.valueOf(order.getParams().getHedgePointDifference()));

        // Record the creation event
        entry.addEvent("CREATED", "Order created. Scheduled for " + formatDateTime(order.getExecutionTime()));

        // Store in memory map for future reference
        orderHistoryMap.put(orderId, entry);

        // Save to disk
        orderHistoryRepository.saveOrderHistory(entry);
    }

    /**
     * Log best options selected for an order
     */
    public void logBestOptionsSelected(String orderId, OptionPair optionPair) {
        if (optionPair == null) {
            LOGGER.info("BEST OPTIONS - No suitable options found for order: " + orderId);

            // Update JSON history
            OrderHistoryEntry entry = getOrCreateHistoryEntry(orderId);
            entry.addEvent("BEST_OPTIONS", "No suitable options found");
            orderHistoryRepository.saveOrderHistory(entry);

            return;
        }

        String message = String.format(
                "BEST OPTIONS - ID: %s, Call: %s (Price: %s), Put: %s (Price: %s), Combined Premium: %s",
                orderId,
                optionPair.hasCallOption() ? optionPair.getCallOption().getTradingSymbol() : "N/A",
                optionPair.hasCallOption() ? optionPair.getCallPrice() : "N/A",
                optionPair.hasPutOption() ? optionPair.getPutOption().getTradingSymbol() : "N/A",
                optionPair.hasPutOption() ? optionPair.getPutPrice() : "N/A",
                getCombinedPremium(optionPair));

        LOGGER.info(message);

        // Update JSON history
        OrderHistoryEntry entry = getOrCreateHistoryEntry(orderId);

        if (optionPair.hasCallOption()) {
            entry.setCallOptionSymbol(optionPair.getCallOption().getTradingSymbol());
            entry.setCallPrice(optionPair.getCallPrice());
        }

        if (optionPair.hasPutOption()) {
            entry.setPutOptionSymbol(optionPair.getPutOption().getTradingSymbol());
            entry.setPutPrice(optionPair.getPutPrice());
        }

        entry.setCombinedPremium(getCombinedPremium(optionPair));

        String eventDescription = String.format("Best options selected - Call: %s, Put: %s, Premium: %s",
                optionPair.hasCallOption() ? optionPair.getCallOption().getTradingSymbol() : "N/A",
                optionPair.hasPutOption() ? optionPair.getPutOption().getTradingSymbol() : "N/A",
                getCombinedPremium(optionPair));

        entry.addEvent("BEST_OPTIONS", eventDescription);
        orderHistoryRepository.saveOrderHistory(entry);
    }

    /**
     * Log hedge orders placed
     */
    public void logHedgeOrdersPlaced(String orderId, OptionPair optionPair) {
        String message = String.format(
                "HEDGE ORDERS PLACED - ID: %s, Call: %s, Put: %s",
                orderId,
                optionPair.hasCallOption() ? optionPair.getCallOption().getTradingSymbol() : "N/A",
                optionPair.hasPutOption() ? optionPair.getPutOption().getTradingSymbol() : "N/A");

        LOGGER.info(message);

        // Update JSON history
        OrderHistoryEntry entry = getOrCreateHistoryEntry(orderId);
        entry.addEvent("HEDGE_ORDERS_PLACED", message);
        orderHistoryRepository.saveOrderHistory(entry);
    }

    /**
     * Log main order placed
     */
    public void logMainOrderPlaced(String orderId, OptionPair optionPair) {
        String message = String.format(
                "MAIN ORDER PLACED - ID: %s, Call: %s, Put: %s",
                orderId,
                optionPair.hasCallOption() ? optionPair.getCallOption().getTradingSymbol() : "N/A",
                optionPair.hasPutOption() ? optionPair.getPutOption().getTradingSymbol() : "N/A");

        LOGGER.info(message);

        // Update JSON history
        OrderHistoryEntry entry = getOrCreateHistoryEntry(orderId);
        entry.setStatus(OrderStatus.COMPLETED);
        entry.addEvent("MAIN_ORDER_PLACED", message);
        orderHistoryRepository.saveOrderHistory(entry);
    }

    /**
     * Log order placed
     */
    public void logOrderPlaced(OrderPlacedEvent event) {
        // Extract order ID from tag, typically in format "TYPE-orderId"
        String tag = event.getTag();
        String orderId = "";

        if (tag != null && !tag.isEmpty()) {
            if (tag.contains("-")) {
                orderId = tag.split("-")[1]; // Get the part after the dash
            } else {
                orderId = tag;
            }
        } else {
            // Generate placeholder if no tag available
            orderId = "UNKNOWN-" + event.getOrderId();
        }

        String message = String.format(
                "ORDER PLACED - Order ID: %s, Tag: %s, Symbol: %s, Lots: %d, Type: %s, Price: %s",
                event.getOrderId(),
                event.getTag(),
                event.getInstrument().getTradingSymbol(),
                event.getQuantity(),
                event.getOrderType(),
                event.getPrice());

        LOGGER.info(message);

        // Update JSON history
        OrderHistoryEntry entry = getOrCreateHistoryEntry(orderId);
        entry.addEvent("ORDER_PLACED", message);
        orderHistoryRepository.saveOrderHistory(entry);
    }

    /**
     * Log order failed
     */
    public void logOrderFailed(OrderFailedEvent event) {
        String orderId = event.getOrderId();
        String strategy = event.getStrategy();
        String instrumentSymbol = event.getInstrumentSymbol();
        OrderType orderType = event.getOrderType();
        String errorMessage = event.getErrorMessage();

        String message = String.format(
                "ORDER FAILED - Strategy: %s, Instrument: %s, Type: %s, Error: %s",
                strategy,
                instrumentSymbol,
                orderType,
                errorMessage);

        LOGGER.warning(message);

        // Update JSON history
        OrderHistoryEntry entry = getOrCreateHistoryEntry(orderId);
        entry.setStatus(OrderStatus.FAILED);
        entry.setFailureReason(errorMessage);
        entry.addEvent("ORDER_FAILED", message);
        orderHistoryRepository.saveOrderHistory(entry);
    }

    /**
     * Log order cleanup
     */
    public void logOrderCleanup(String orderId, String details) {
        String message = "ORDER CLEANUP - ID: " + orderId + ", Details: " + details;
        LOGGER.info(message);

        // Update JSON history
        OrderHistoryEntry entry = getOrCreateHistoryEntry(orderId);
        entry.addEvent("ORDER_CLEANUP", message);
        orderHistoryRepository.saveOrderHistory(entry);

        // Remove from in-memory map once order is completed and logged
        orderHistoryMap.remove(orderId);
    }

    /**
     * Log order status change
     */
    public void logOrderStatusChange(String orderId, OrderStatus oldStatus, OrderStatus newStatus) {
        String message = String.format(
                "STATUS CHANGE - ID: %s, Old: %s, New: %s",
                orderId,
                oldStatus,
                newStatus);

        LOGGER.info(message);

        // Update JSON history
        OrderHistoryEntry entry = getOrCreateHistoryEntry(orderId);
        entry.setStatus(newStatus);
        entry.addEvent("STATUS_CHANGE", message);
        orderHistoryRepository.saveOrderHistory(entry);
    }

    /**
     * Format a date time for logging
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "N/A";
        }
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /**
     * Get order history as JSON
     */
    public OrderHistoryEntry getOrderHistoryJson(String orderId) {
        // First check the in-memory cache
        OrderHistoryEntry entry = orderHistoryMap.get(orderId);
        if (entry != null) {
            return entry;
        }

        // If not in memory, try the repository
        return orderHistoryRepository.getOrderHistory(orderId);
    }

    /**
     * Clean up resources when shutting down
     */
    public void shutdown() {
        LOGGER.info("OrderLoggerService shutdown");
    }
}