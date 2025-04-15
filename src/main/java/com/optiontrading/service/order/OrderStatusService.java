package com.optiontrading.service.order;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.optiontrading.service.model.OrderStatus;
import com.optiontrading.service.trading.TradingService;
import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Service to periodically check order status from broker
 */
@Singleton
public class OrderStatusService {
    private static final Logger LOGGER = Logger.getLogger(OrderStatusService.class.getName());
    private static final long CHECK_INTERVAL_SECONDS = 10;

    private final TradingService tradingService;
    private final OrderRepository orderRepository;
    private final EventBus eventBus;
    private final ScheduledExecutorService scheduler;

    // Track orders we're monitoring for status updates
    private final Map<String, Boolean> monitoredOrders = new ConcurrentHashMap<>();

    /**
     * Constructor with dependencies
     * 
     * @param tradingService  the trading service
     * @param orderRepository the order repository
     * @param eventBus        the event bus
     */
    @Inject
    public OrderStatusService(
            TradingService tradingService,
            OrderRepository orderRepository,
            EventBus eventBus) {
        this.tradingService = tradingService;
        this.orderRepository = orderRepository;
        this.eventBus = eventBus;

        // Create single-threaded scheduler for periodic checks
        this.scheduler = Executors.newSingleThreadScheduledExecutor();

        LOGGER.info("OrderStatusService initialized");

        // Subscribe to events that include order IDs
        eventBus.subscribe(MainOrderPlacedEvent.class, new EventSubscriber<MainOrderPlacedEvent>() {
            @Override
            public void onEvent(MainOrderPlacedEvent event) {
                String orderId = event.getOrderId();
                if (orderId != null && !orderId.isEmpty()) {
                    addOrderToMonitor(orderId);
                }
            }
        });

        // Start periodic checking
        startScheduledChecks();
    }

    /**
     * Start the scheduled status checks
     */
    private void startScheduledChecks() {
        LOGGER.info("Starting scheduled order status checks every " + CHECK_INTERVAL_SECONDS + " seconds");

        scheduler.scheduleAtFixedRate(
                this::checkOrderStatuses,
                CHECK_INTERVAL_SECONDS,
                CHECK_INTERVAL_SECONDS,
                TimeUnit.SECONDS);
    }

    /**
     * Add an order ID to monitor for status updates
     * 
     * @param orderId the broker order ID to monitor
     */
    public void addOrderToMonitor(String orderId) {
        if (orderId != null && !orderId.isEmpty()) {
            LOGGER.info("Adding order to status monitoring: " + orderId);
            monitoredOrders.put(orderId, false);
        }
    }

    /**
     * Check all monitored orders for status updates
     */
    private void checkOrderStatuses() {
        try {
            LOGGER.fine("Checking status for " + monitoredOrders.size() + " monitored orders");

            // Only check if we have orders to monitor
            if (monitoredOrders.isEmpty()) {
                return;
            }

            // Copy the keys to avoid concurrent modification
            for (String orderId : monitoredOrders.keySet().toArray(new String[0])) {
                checkOrderStatus(orderId);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during order status check", e);
        }
    }

    /**
     * Check a specific order's status
     * 
     * @param orderId the broker order ID to check
     */
    private void checkOrderStatus(String orderId) {
        try {
            OrderStatus currentStatus = tradingService.getOrderStatus(orderId);

            LOGGER.fine("Order " + orderId + " status: " + currentStatus);

            // If the order has reached a terminal state, stop monitoring it
            if (currentStatus == OrderStatus.COMPLETED ||
                    currentStatus == OrderStatus.FAILED ||
                    currentStatus == OrderStatus.CANCELLED) {

                LOGGER.info("Order " + orderId + " reached terminal state: " + currentStatus);

                // Remove from monitored orders
                boolean wasComplete = monitoredOrders.remove(orderId);

                // If the order was newly completed or failed, notify the system
                if (!wasComplete) {
                    // Publish an event to notify the system about the status change
                    eventBus.publish(new OrderStatusChangedEvent(orderId, currentStatus));
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking status for order: " + orderId, e);
        }
    }

    /**
     * Shutdown the service
     */
    public void shutdown() {
        LOGGER.info("Shutting down OrderStatusService");
        scheduler.shutdown();
        try {
            if (!scheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                scheduler.shutdownNow();
            }
        } catch (InterruptedException e) {
            scheduler.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }
}