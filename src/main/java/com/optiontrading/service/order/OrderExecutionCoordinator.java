package com.optiontrading.service.order;

import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.resources.ResourceManager;
import com.optiontrading.resources.TimerManager;
import com.optiontrading.service.instrument.InstrumentService;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OrderStatus;
import com.optiontrading.service.model.OptionPair;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.option.BestOptionsUpdatedEvent;
import com.optiontrading.service.option.OptionChainService;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Coordinates the execution of scheduled orders
 */
public class OrderExecutionCoordinator {
    private static final Logger LOGGER = Logger.getLogger(OrderExecutionCoordinator.class.getName());
    private static final OrderExecutionCoordinator INSTANCE = new OrderExecutionCoordinator();

    // Map of order ID to active option chain service
    private final Map<String, OptionChainService> optionChainServices = new ConcurrentHashMap<>();

    // Map of order ID to best option pair
    private final Map<String, OptionPair> bestOptionPairs = new ConcurrentHashMap<>();

    // Services and managers
    private final OrderRepository orderRepository;
    private final InstrumentService instrumentService;
    private final TimerManager timerManager;
    private final EventBus eventBus;

    // Private constructor for singleton
    private OrderExecutionCoordinator() {
        this.orderRepository = OrderRepository.getInstance();
        this.instrumentService = InstrumentService.getInstance();
        this.timerManager = ResourceManager.getInstance().getTimerManager();
        this.eventBus = EventBus.getInstance();

        // Subscribe to events
        subscribeToEvents();

        // Start checking for orders to execute
        startOrderCheckTimer();

        LOGGER.info("Initialized OrderExecutionCoordinator");
    }

    /**
     * Get the singleton instance
     */
    public static OrderExecutionCoordinator getInstance() {
        return INSTANCE;
    }

    /**
     * Subscribe to relevant events
     */
    private void subscribeToEvents() {
        // Subscribe to order created events
        eventBus.subscribe(OrderCreatedEvent.class, new EventSubscriber<OrderCreatedEvent>() {
            @Override
            public void onEvent(OrderCreatedEvent event) {
                scheduleOrderExecution(event.getOrder());
            }
        });

        // Subscribe to best options updated events
        eventBus.subscribe(BestOptionsUpdatedEvent.class, new EventSubscriber<BestOptionsUpdatedEvent>() {
            @Override
            public void onEvent(BestOptionsUpdatedEvent event) {
                handleBestOptionsUpdated(event);
            }
        });
    }

    /**
     * Start timer to check for orders to execute
     */
    private void startOrderCheckTimer() {
        // Check every minute for orders to execute in the next 5 minutes
        timerManager.scheduleAtFixedRate("OrderCheck-Timer", true, new TimerTask() {
            @Override
            public void run() {
                checkOrdersToExecute();
            }
        }, 0, 60000);
    }

    /**
     * Check for orders that need to be executed soon
     */
    private void checkOrdersToExecute() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lookAhead = now.plusMinutes(5);

            List<ScheduledOrder> ordersToExecute = orderRepository.getOrdersToExecute(now, lookAhead);

            LOGGER.info("Found " + ordersToExecute.size() + " orders to execute in the next 5 minutes");

            for (ScheduledOrder order : ordersToExecute) {
                // If we're not already preparing this order, start the preparation
                if (!optionChainServices.containsKey(order.getOrderId())) {
                    scheduleOrderPreparation(order);
                }
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error checking orders to execute", e);
        }
    }

    /**
     * Schedule the execution sequence for an order
     * 
     * @param order the order to schedule
     */
    private void scheduleOrderExecution(ScheduledOrder order) {
        LOGGER.info("Scheduling execution for order: " + order);

        // Check if the execution time is in the past
        if (order.getExecutionTime().isBefore(LocalDateTime.now())) {
            LOGGER.warning("Order " + order.getOrderId() + " has execution time in the past, marking as FAILED");
            orderRepository.updateOrderStatus(order.getOrderId(), OrderStatus.FAILED);
            return;
        }

        // If execution is within 5 minutes, start preparation immediately
        if (order.getExecutionTime().isBefore(LocalDateTime.now().plusMinutes(5))) {
            scheduleOrderPreparation(order);
        }
    }

    /**
     * Schedule preparation for an order (T-25 seconds)
     * 
     * @param order the order to prepare
     */
    private void scheduleOrderPreparation(ScheduledOrder order) {
        // Calculate when to start preparation (T-25 seconds)
        LocalDateTime preparationTime = order.getExecutionTime().minusSeconds(25);
        long delay = calculateDelay(preparationTime);

        if (delay < 0) {
            // If we're already past the preparation time, start immediately
            startOrderPreparation(order);
        } else {
            // Otherwise, schedule preparation
            LOGGER.info("Scheduling preparation for order " + order.getOrderId() +
                    " in " + delay + "ms at " + preparationTime);

            timerManager.createTimer(false).schedule(new TimerTask() {
                @Override
                public void run() {
                    startOrderPreparation(order);
                }
            }, delay);
        }
    }

    /**
     * Start preparing an order for execution (T-25)
     * 
     * @param order the order to prepare
     */
    private void startOrderPreparation(ScheduledOrder order) {
        String orderId = order.getOrderId();

        LOGGER.info("Starting preparation for order: " + orderId);

        try {
            // Update order status
            orderRepository.updateOrderStatus(orderId, OrderStatus.PREPARING);

            // Get eligible instruments
            String indexSymbol = order.getParams().getIndexSymbol();
            BigDecimal spotPrice = instrumentService.getIndexSpotPrice(indexSymbol);

            if (spotPrice == null) {
                LOGGER.severe("Cannot find spot price for index: " + indexSymbol);
                orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
                return;
            }

            List<Instrument> eligibleInstruments = instrumentService.filterInstruments(
                    indexSymbol,
                    order.getParams().getExpiryDate(),
                    spotPrice,
                    order.getParams().getThreshold());

            if (eligibleInstruments.isEmpty()) {
                LOGGER.severe("No eligible instruments found for order: " + orderId);
                orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
                return;
            }

            LOGGER.info("Found " + eligibleInstruments.size() + " eligible instruments for order: " + orderId);

            // Create option chain service for monitoring options
            OptionChainService service = new OptionChainService(
                    orderId,
                    order.getParams().getTargetPremium());

            // Start monitoring instruments
            service.monitorInstruments(eligibleInstruments);
            optionChainServices.put(orderId, service);

            // Update order status
            orderRepository.updateOrderStatus(orderId, OrderStatus.MONITORING);

            // Schedule hedge orders if enabled
            if (order.getParams().isHedgingEnabled()) {
                scheduleHedgeOrders(order);
            }

            // Schedule main order execution
            scheduleMainOrderExecution(order);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error preparing order: " + orderId, e);
            orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
        }
    }

    /**
     * Schedule hedge orders execution (T-10 seconds)
     * 
     * @param order the order
     */
    private void scheduleHedgeOrders(ScheduledOrder order) {
        // Calculate when to execute hedge orders (T-10 seconds)
        LocalDateTime hedgeTime = order.getExecutionTime().minusSeconds(10);
        long delay = calculateDelay(hedgeTime);

        LOGGER.info("Scheduling hedge orders for order " + order.getOrderId() +
                " in " + delay + "ms at " + hedgeTime);

        timerManager.createTimer(false).schedule(new TimerTask() {
            @Override
            public void run() {
                executeHedgeOrders(order);
            }
        }, delay);
    }

    /**
     * Execute hedge orders (T-10)
     * 
     * @param order the order
     */
    private void executeHedgeOrders(ScheduledOrder order) {
        String orderId = order.getOrderId();

        LOGGER.info("Executing hedge orders for order: " + orderId);

        try {
            // Update order status
            orderRepository.updateOrderStatus(orderId, OrderStatus.HEDGING);

            // Get the option chain service and best pair
            OptionChainService service = optionChainServices.get(orderId);
            if (service == null) {
                LOGGER.severe("Option chain service not found for order: " + orderId);
                return;
            }

            OptionPair bestPair = service.getCurrentBestPair();
            if (bestPair == null || !bestPair.isComplete()) {
                LOGGER.warning("No complete option pair available for hedging order: " + orderId);
                return;
            }

            // Place hedge orders
            // In a real implementation, this would call the broker API
            // For now, we just log the action
            int hedgePointDifference = order.getParams().getHedgePointDifference();

            LOGGER.info("Placing hedge orders for order " + orderId +
                    " with " + bestPair + " and hedge point difference: " + hedgePointDifference);

            // Publish event (you would create this class)
            eventBus.publishAsync(new HedgeOrdersPlacedEvent(orderId, bestPair));

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing hedge orders for order: " + orderId, e);
        }
    }

    /**
     * Schedule main order execution (T-0)
     * 
     * @param order the order
     */
    private void scheduleMainOrderExecution(ScheduledOrder order) {
        // Calculate when to execute main order (T-0)
        LocalDateTime executionTime = order.getExecutionTime();
        long delay = calculateDelay(executionTime);

        LOGGER.info("Scheduling main order execution for order " + order.getOrderId() +
                " in " + delay + "ms at " + executionTime);

        timerManager.createTimer(false).schedule(new TimerTask() {
            @Override
            public void run() {
                executeMainOrder(order);
            }
        }, delay);
    }

    /**
     * Execute the main order (T-0)
     * 
     * @param order the order
     */
    private void executeMainOrder(ScheduledOrder order) {
        String orderId = order.getOrderId();

        LOGGER.info("Executing main order: " + orderId);

        try {
            // Update order status
            orderRepository.updateOrderStatus(orderId, OrderStatus.EXECUTING);

            // Get the option chain service and best pair
            OptionChainService service = optionChainServices.get(orderId);
            if (service == null) {
                LOGGER.severe("Option chain service not found for order: " + orderId);
                orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
                return;
            }

            OptionPair bestPair = service.getCurrentBestPair();
            if (bestPair == null || !bestPair.isComplete()) {
                LOGGER.warning("No complete option pair available for order: " + orderId);
                orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
                return;
            }

            // Place main order
            // In a real implementation, this would call the broker API
            // For now, we just log the action
            LOGGER.info("Placing main order " + orderId + " with " + bestPair +
                    ", type: " + order.getParams().getOrderType() +
                    ", lots: " + order.getParams().getLots());

            // Publish event (you would create this class)
            eventBus.publishAsync(new MainOrderPlacedEvent(orderId, bestPair));

            // Update order status
            orderRepository.updateOrderStatus(orderId, OrderStatus.COMPLETED);

            // Clean up
            cleanupOrder(orderId);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing main order: " + orderId, e);
            orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
            cleanupOrder(orderId);
        }
    }

    /**
     * Handle best options updated event
     * 
     * @param event the event
     */
    private void handleBestOptionsUpdated(BestOptionsUpdatedEvent event) {
        String orderId = event.getOrderId();
        OptionPair optionPair = event.getOptionPair();

        // Store the best option pair
        bestOptionPairs.put(orderId, optionPair);
    }

    /**
     * Clean up resources for an order
     * 
     * @param orderId the order ID
     */
    private void cleanupOrder(String orderId) {
        // Shutdown option chain service
        OptionChainService service = optionChainServices.remove(orderId);
        if (service != null) {
            service.shutdown();
        }

        // Remove best option pair
        bestOptionPairs.remove(orderId);
    }

    /**
     * Calculate delay in milliseconds to a target time
     * 
     * @param targetTime the target time
     * @return delay in milliseconds (can be negative if target time is in the past)
     */
    private long calculateDelay(LocalDateTime targetTime) {
        return ChronoUnit.MILLIS.between(LocalDateTime.now(), targetTime);
    }

    /**
     * Shutdown the coordinator
     */
    public void shutdown() {
        LOGGER.info("Shutting down OrderExecutionCoordinator");

        // Shutdown all option chain services
        for (String orderId : optionChainServices.keySet()) {
            cleanupOrder(orderId);
        }
    }
}