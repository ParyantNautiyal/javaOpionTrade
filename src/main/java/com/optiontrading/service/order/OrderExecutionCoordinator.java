package com.optiontrading.service.order;

import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.resources.TimerManager;
import com.optiontrading.service.instrument.InstrumentService;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OrderStatus;
import com.optiontrading.service.model.OptionPair;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.option.BestOptionsUpdatedEvent;
import com.optiontrading.service.option.OptionChainService;
import com.optiontrading.service.trading.TradingService;
import com.optiontrading.service.model.OptionType;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.google.inject.Provider;
import com.optiontrading.config.ConfigurationManager;
import com.optiontrading.resources.ThreadManager;
import com.optiontrading.service.market.MarketDataService;
import com.optiontrading.service.position.PositionWatchlistService;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.Set;
import java.util.Collections;
import java.util.HashSet;

/**
 * Coordinates the execution of scheduled orders
 */
@Singleton
public class OrderExecutionCoordinator {
    private static final Logger LOGGER = Logger.getLogger(OrderExecutionCoordinator.class.getName());

    // Map of order ID to active option chain service
    private final Map<String, OptionChainService> optionChainServices = new ConcurrentHashMap<>();

    // Map of order ID to best option pair
    private final ConcurrentMap<String, OptionPair> bestOptionPairs = new ConcurrentHashMap<>();

    // Track orders that are currently being scheduled for preparation to prevent
    // duplicates
    private final Set<String> preparationScheduled = Collections.synchronizedSet(new HashSet<>());

    // Track which execution stages are currently running to prevent duplicate
    // executions
    private final Set<String> currentlyExecuting = Collections.synchronizedSet(new HashSet<>());

    // Services and managers
    private final OrderRepository orderRepository;
    private final InstrumentService instrumentService;
    private final TimerManager timerManager;
    private final EventBus eventBus;
    private final TradingService tradingService;
    private final Provider<OptionChainService> optionChainServiceProvider;
    private final ConfigurationManager configManager;
    private final MarketDataService marketDataService;
    private final ThreadManager threadManager;
    private final PositionWatchlistService positionWatchlistService;

    // Track which orders have already had MainOrderPlacedEvent published
    private final Set<String> publishedMainOrderEvents = Collections.synchronizedSet(new HashSet<>());

    /**
     * Constructor with dependency injection
     */
    @Inject
    public OrderExecutionCoordinator(
            OrderRepository orderRepository,
            InstrumentService instrumentService,
            TimerManager timerManager,
            EventBus eventBus,
            TradingService tradingService,
            Provider<OptionChainService> optionChainServiceProvider,
            ConfigurationManager configManager,
            MarketDataService marketDataService,
            ThreadManager threadManager,
            PositionWatchlistService positionWatchlistService) {

        this.orderRepository = orderRepository;
        this.instrumentService = instrumentService;
        this.timerManager = timerManager;
        this.eventBus = eventBus;
        this.tradingService = tradingService;
        this.optionChainServiceProvider = optionChainServiceProvider;
        this.configManager = configManager;
        this.marketDataService = marketDataService;
        this.threadManager = threadManager;
        this.positionWatchlistService = positionWatchlistService;

        // Subscribe to events
        subscribeToEvents();

        // Start checking for orders to execute
        startOrderCheckTimer();

        LOGGER.info("Initialized OrderExecutionCoordinator with dependency injection");
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
        // Check every 10 seconds for orders to execute in the next 6 minutes
        timerManager.scheduleAtFixedRate("OrderCheck-Timer", true, new TimerTask() {
            @Override
            public void run() {
                checkOrdersToExecute();
            }
        }, 0, 10000); // Changed from 60000ms to 10000ms (10 seconds)

        LOGGER.info("Started order check timer - checking every 10 seconds for upcoming orders");
    }

    /**
     * Check for orders that need to be executed soon
     */
    private void checkOrdersToExecute() {
        try {
            LocalDateTime now = LocalDateTime.now();
            LocalDateTime lookAhead = now.plusMinutes(6); // Changed from 5 to 6 minutes

            LOGGER.fine("Scanning for orders between " + formatDateTime(now) + " and " + formatDateTime(lookAhead));

            // Log all active scheduled orders with their time remaining
            List<ScheduledOrder> allScheduledOrders = orderRepository.getAllOrders().stream()
                    .filter(order -> order.getStatus() == OrderStatus.SCHEDULED)
                    .collect(java.util.stream.Collectors.toList());

            if (!allScheduledOrders.isEmpty()) {
                LOGGER.info("Found " + allScheduledOrders.size() + " scheduled orders");

                for (ScheduledOrder order : allScheduledOrders) {
                    LocalDateTime executionTime = order.getExecutionTime();
                    long minutesRemaining = ChronoUnit.MINUTES.between(now, executionTime);
                    long secondsRemaining = ChronoUnit.SECONDS.between(now, executionTime) % 60;

                    LOGGER.info("Order " + order.getOrderId() +
                            " | Index: " + order.getParams().getIndexSymbol() +
                            " | Execution Time: " + formatDateTime(executionTime) +
                            " | Time Remaining: " + minutesRemaining + " min " + secondsRemaining + " sec");
                }
            }

            // Find orders to execute in the next 6 minutes
            List<ScheduledOrder> ordersToExecute = orderRepository.getOrdersToExecute(now, lookAhead);

            LOGGER.info("Found " + ordersToExecute.size() + " orders to execute in the next 6 minutes");

            if (ordersToExecute.isEmpty()) {
                LOGGER.fine("No orders found for execution in the next 6 minutes");
            } else {
                for (ScheduledOrder order : ordersToExecute) {
                    LocalDateTime executionTime = order.getExecutionTime();
                    long secondsRemaining = ChronoUnit.SECONDS.between(now, executionTime);

                    LOGGER.info("Order due soon: " + order.getOrderId() +
                            " | Execution Time: " + formatDateTime(executionTime) +
                            " | Seconds remaining: " + secondsRemaining);
                }
            }

            for (ScheduledOrder order : ordersToExecute) {
                // If we're not already preparing this order, start the preparation
                synchronized (preparationScheduled) {
                    if (!optionChainServices.containsKey(order.getOrderId())
                            && !preparationScheduled.contains(order.getOrderId())) {
                        LOGGER.info("Order ready for preparation: " + order.getOrderId() +
                                ", execution time: " + formatDateTime(order.getExecutionTime()));
                        preparationScheduled.add(order.getOrderId());
                        scheduleOrderPreparation(order);
                    }
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

        // Pre-filter instruments immediately to reduce API overhead later
        preFilterInstrumentsForOrder(order);

        // If execution is within 5 minutes, start preparation immediately
        if (order.getExecutionTime().isBefore(LocalDateTime.now().plusMinutes(5))) {
            scheduleOrderPreparation(order);
        }
    }

    /**
     * Pre-filter instruments based on index and expiry when order is scheduled
     * 
     * @param order the scheduled order
     */
    private void preFilterInstrumentsForOrder(ScheduledOrder order) {
        try {
            String orderId = order.getOrderId();
            String indexSymbol = order.getParams().getIndexSymbol();
            LocalDate expiryDate = order.getParams().getExpiryDate();

            LOGGER.info("Pre-filtering instruments for order " + orderId +
                    " (index: " + indexSymbol + ", expiry: " + expiryDate + ")");

            // Get pre-filtered instruments based only on index and expiry
            List<Instrument> preFilteredInstruments = instrumentService.preFilterInstruments(indexSymbol, expiryDate);

            // Store pre-filtered list with the order repository for later use
            orderRepository.storePreFilteredInstruments(orderId, preFilteredInstruments);

            LOGGER.info("Stored " + preFilteredInstruments.size() +
                    " pre-filtered instruments for order " + orderId);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error pre-filtering instruments for order " +
                    order.getOrderId(), e);
            // Continue with order scheduling despite pre-filtering error
            // We'll retry filtering at T-25s if needed
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
        String executionId = orderId + "-PREP";

        // Check if this preparation is already being executed
        synchronized (currentlyExecuting) {
            if (currentlyExecuting.contains(executionId)) {
                LOGGER.info("Skipping duplicate preparation execution for: " + orderId);
                return;
            }
            currentlyExecuting.add(executionId);
        }

        try {
            LOGGER.info("Starting preparation for order: " + orderId);

            String indexSymbol = order.getParams().getIndexSymbol();
            LOGGER.info("Preparation: Checking spot price for index: " + indexSymbol);

            // Log attempt
            LOGGER.info("Execution attempt: " + orderId + " - PREPARATION");

            // Get spot price for the index
            BigDecimal spotPrice = instrumentService.getIndexSpotPrice(indexSymbol);
            if (spotPrice == null) {
                LOGGER.severe("Preparation failed: Cannot find spot price for index: " + indexSymbol);
                orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
                return;
            }

            LOGGER.fine("Spot price: " + orderId + " - " + indexSymbol + " - " + spotPrice);

            // Get pre-filtered instruments (by index and expiry)
            List<Instrument> preFilteredInstruments = orderRepository.getPreFilteredInstruments(orderId);

            // If pre-filtered list is empty or not found, try filtering again
            if (preFilteredInstruments == null || preFilteredInstruments.isEmpty()) {
                LOGGER.warning("No pre-filtered instruments found for order " + orderId +
                        ", filtering now");
                preFilteredInstruments = instrumentService.preFilterInstruments(
                        indexSymbol, order.getParams().getExpiryDate());
            }

            // Apply strike filter based on current spot price
            double threshold = order.getParams().getThreshold();
            List<Instrument> eligibleInstruments = instrumentService.applyStrikeFilter(
                    preFilteredInstruments, spotPrice, threshold);

            if (eligibleInstruments.isEmpty()) {
                LOGGER.severe("No eligible instruments found for order: " + orderId);
                orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
                return;
            }

            LOGGER.info("Found " + eligibleInstruments.size() + " eligible instruments for order: " + orderId);

            // Create option chain service for analyzing options
            OptionChainService optionChainService = optionChainServiceProvider.get();
            optionChainService.initialize(orderId, order.getParams().getTargetPremium());

            // Store the option chain service
            optionChainServices.put(orderId, optionChainService);

            // Start monitoring eligible instruments
            optionChainService.monitorInstruments(eligibleInstruments);

            // Update order status
            orderRepository.updateOrderStatus(orderId, OrderStatus.ANALYZING);

            // Schedule hedge orders for T-10s
            scheduleHedgeOrders(order);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in order preparation for " + orderId + ": " + e.getMessage(), e);
            orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
        } finally {
            // Always remove from preparation scheduled set even if an exception occurs
            preparationScheduled.remove(orderId);

            // Always remove from currently executing set
            currentlyExecuting.remove(executionId);
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
     * Execute hedge orders at T-10s
     * 
     * @param order the order to execute hedge orders for
     */
    private void executeHedgeOrders(ScheduledOrder order) {
        String orderId = order.getOrderId();
        String executionId = orderId + "-HEDGE";

        // Check if these hedge orders are already being executed
        synchronized (currentlyExecuting) {
            if (currentlyExecuting.contains(executionId)) {
                LOGGER.info("Skipping duplicate hedge order execution for: " + orderId);
                return;
            }
            currentlyExecuting.add(executionId);
        }

        LOGGER.info("Executing hedge orders for: " + orderId);

        // Log attempt
        LOGGER.info("Execution attempt: " + orderId + " - HEDGING");

        try {
            // Get the best option pair
            OptionPair bestPair = bestOptionPairs.get(orderId);
            if (bestPair == null) {
                LOGGER.warning("No best option pair found for order: " + orderId);
                orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
                cleanupOrder(orderId);
                return;
            }

            // Check if hedging is enabled
            if (!order.getParams().isHedgingEnabled()) {
                LOGGER.info("Hedging not enabled for order: " + orderId + ", skipping hedge orders");
                orderRepository.updateOrderStatus(orderId, OrderStatus.MONITORING);

                // Schedule main order execution
                scheduleMainOrderExecution(order);
                return;
            }

            // Get hedge point difference
            int hedgePoints = order.getParams().getHedgePointDifference();

            // Get the call and put options from the best pair
            Instrument callOption = bestPair.getCallOption();
            Instrument putOption = bestPair.getPutOption();

            // Calculate hedge strikes
            BigDecimal callHedgeStrike = callOption.getStrikePrice().add(BigDecimal.valueOf(hedgePoints));
            BigDecimal putHedgeStrike = putOption.getStrikePrice().subtract(BigDecimal.valueOf(hedgePoints));

            LOGGER.info("Calculated hedge strikes - Call: " + callHedgeStrike + ", Put: " + putHedgeStrike);

            // Find hedge instruments for the calculated strikes
            List<Instrument> callHedgeOptions = instrumentService.findOptionsAtStrike(
                    callOption.getUnderlyingSymbol(),
                    callOption.getExpiryDate(),
                    callHedgeStrike,
                    OptionType.CALL);

            List<Instrument> putHedgeOptions = instrumentService.findOptionsAtStrike(
                    putOption.getUnderlyingSymbol(),
                    putOption.getExpiryDate(),
                    putHedgeStrike,
                    OptionType.PUT);

            // Place call hedge order
            if (!callHedgeOptions.isEmpty()) {
                Instrument callHedgeOption = callHedgeOptions.get(0);
                LOGGER.info("Placing call hedge order at strike " + callHedgeStrike +
                        ": " + callHedgeOption.getTradingSymbol());

                String brokerId = tradingService.placeOrder(
                        callHedgeOption,
                        order.getParams().getLots(),
                        null, // Use market price
                        order.getParams().getOrderType(),
                        "HEDGE-" + orderId);

                if (brokerId != null) {
                    LOGGER.info("Call hedge order placed successfully, broker ID: " + brokerId);
                } else {
                    LOGGER.warning("Failed to place call hedge order");
                }
            } else {
                LOGGER.warning("No hedge option found at strike " + callHedgeStrike);
            }

            // Add 500ms delay between orders to respect API rate limits
            try {
                Thread.sleep(500);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Interrupted during order delay");
            }

            // Place put hedge order
            if (!putHedgeOptions.isEmpty()) {
                Instrument putHedgeOption = putHedgeOptions.get(0);
                LOGGER.info("Placing put hedge order at strike " + putHedgeStrike +
                        ": " + putHedgeOption.getTradingSymbol());

                String brokerId = tradingService.placeOrder(
                        putHedgeOption,
                        order.getParams().getLots(),
                        null, // Use market price
                        order.getParams().getOrderType(),
                        "HEDGE-" + orderId);

                if (brokerId != null) {
                    LOGGER.info("Put hedge order placed successfully, broker ID: " + brokerId);
                } else {
                    LOGGER.warning("Failed to place put hedge order");
                }
            } else {
                LOGGER.warning("No hedge option found at strike " + putHedgeStrike);
            }

            // Update order status
            orderRepository.updateOrderStatus(orderId, OrderStatus.MONITORING);

            // Publish event
            eventBus.publishAsync(new HedgeOrdersPlacedEvent(orderId, bestPair));

            // Schedule main order execution
            scheduleMainOrderExecution(order);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing hedge orders for order: " + orderId, e);
            orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
            cleanupOrder(orderId);
        } finally {
            // Always remove from currently executing set
            currentlyExecuting.remove(executionId);
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
     * Execute main order at T-0
     * 
     * @param order the order to execute
     */
    private void executeMainOrder(ScheduledOrder order) {
        String orderId = order.getOrderId();
        String executionId = orderId + "-MAIN";

        // Check if this main order is already being executed
        synchronized (currentlyExecuting) {
            if (currentlyExecuting.contains(executionId)) {
                LOGGER.info("Skipping duplicate main order execution for: " + orderId);
                return;
            }
            currentlyExecuting.add(executionId);
        }

        LOGGER.info("Executing main order: " + orderId);

        // Log attempt
        LOGGER.info("Execution attempt: " + orderId + " - MAIN EXECUTION");

        try {
            // Get the best option pair
            OptionPair bestPair = bestOptionPairs.get(orderId);
            if (bestPair == null) {
                LOGGER.warning("No best option pair found for order: " + orderId);
                orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
                cleanupOrder(orderId);
                return;
            }

            // Get the order parameters
            com.optiontrading.service.model.OrderType orderType = order.getParams().getOrderType();
            int lots = order.getParams().getLots();

            // Get the call and put options from the best pair
            Instrument callOption = bestPair.getCallOption();
            Instrument putOption = bestPair.getPutOption();
            BigDecimal callPrice = bestPair.getCallPrice();
            BigDecimal putPrice = bestPair.getPutPrice();

            LOGGER.info("Executing " + orderType + " order for " + lots + " lots - " +
                    "Call: " + callOption.getTradingSymbol() + " @ " + callPrice + ", " +
                    "Put: " + putOption.getTradingSymbol() + " @ " + putPrice);

            // Place call option order
            LOGGER.info("Placing order for call option: " + callOption.getInstrumentId());
            String callOrderId = null;
            try {
                callOrderId = tradingService.placeOrder(
                        callOption,
                        lots,
                        callPrice, // Use the price from the option pair
                        orderType,
                        "MAIN-" + orderId);

                if (callOrderId != null) {
                    LOGGER.info("Call option order placed successfully, broker ID: " + callOrderId);
                } else {
                    LOGGER.warning("Failed to place call option order");
                }
            } catch (RuntimeException e) {
                // Specially handle market closed errors
                if (e.getMessage() != null && e.getMessage().startsWith("MARKET_CLOSED:")) {
                    LOGGER.severe("Market closed error detected during call option order placement: " + e.getMessage());
                    orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
                    cleanupOrder(orderId);
                    return;
                }
                throw e; // Re-throw other exceptions
            }

            // Get the delay between orders from configuration
            int orderExecutionDelay = configManager.getInt("order.execution.delay.ms", 150);

            // Add delay between orders to respect API rate limits
            try {
                LOGGER.fine("Delaying " + orderExecutionDelay + "ms between orders");
                Thread.sleep(orderExecutionDelay);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                LOGGER.warning("Interrupted during order delay");
            }

            // Place put option order
            LOGGER.info("Placing order for put option: " + putOption.getInstrumentId());
            String putOrderId = null;
            try {
                putOrderId = tradingService.placeOrder(
                        putOption,
                        lots,
                        putPrice, // Use the price from the option pair
                        orderType,
                        "MAIN-" + orderId);

                if (putOrderId != null) {
                    LOGGER.info("Put option order placed successfully, broker ID: " + putOrderId);
                } else {
                    LOGGER.warning("Failed to place put option order");
                }
            } catch (RuntimeException e) {
                // Specially handle market closed errors
                if (e.getMessage() != null && e.getMessage().startsWith("MARKET_CLOSED:")) {
                    LOGGER.severe("Market closed error detected during put option order placement: " + e.getMessage());
                    orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
                    cleanupOrder(orderId);
                    return;
                }
                throw e; // Re-throw other exceptions
            }

            // Update order status
            boolean bothOrdersPlaced = (callOrderId != null && putOrderId != null);
            orderRepository.updateOrderStatus(orderId,
                    bothOrdersPlaced ? OrderStatus.COMPLETED : OrderStatus.FAILED);

            // Only publish the event if the orders were successfully placed
            if (bothOrdersPlaced) {
                // Publish event - use synchronous publish to ensure immediate handling
                // Check if we've already published an event for this order
                if (!publishedMainOrderEvents.contains(orderId)) {
                    LOGGER.info("Publishing MainOrderPlacedEvent for order: " + orderId);
                    try {
                        eventBus.publish(new MainOrderPlacedEvent(orderId, bestPair));
                        LOGGER.info("MainOrderPlacedEvent published successfully");
                        // Mark this order as having had its event published
                        publishedMainOrderEvents.add(orderId);
                    } catch (Exception e) {
                        LOGGER.severe("Error publishing MainOrderPlacedEvent: " + e.getMessage());
                        e.printStackTrace();
                    }
                } else {
                    LOGGER.info("Skipped publishing duplicate MainOrderPlacedEvent for order: " + orderId);
                }
            } else {
                LOGGER.warning("Not publishing MainOrderPlacedEvent because one or both orders failed");
            }

            // Clean up
            cleanupOrder(orderId);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error executing main order: " + orderId, e);
            orderRepository.updateOrderStatus(orderId, OrderStatus.FAILED);
            cleanupOrder(orderId);
        } finally {
            // Always remove from currently executing set
            currentlyExecuting.remove(executionId);
        }
    }

    /**
     * Handle best options updated event
     * 
     * @param event the event
     */
    private void handleBestOptionsUpdated(BestOptionsUpdatedEvent event) {
        try {
            String orderId = event.getOrderId();
            OptionPair optionPair = event.getOptionPair();

            LOGGER.info("Received BestOptionsUpdatedEvent for order: " + orderId);

            if (optionPair == null) {
                LOGGER.warning("Ignoring BestOptionsUpdatedEvent with null optionPair for order: " + orderId);
                return;
            }

            // Store the best option pair
            bestOptionPairs.put(orderId, optionPair);

            LOGGER.info("Updated best option pair for order: " + orderId +
                    " - call: " + (optionPair.hasCallOption() ? optionPair.getCallOption().getTradingSymbol() : "none")
                    +
                    " - put: " + (optionPair.hasPutOption() ? optionPair.getPutOption().getTradingSymbol() : "none"));

            // Ensure we have the best pair when executing order
            if (!optionChainServices.containsKey(orderId)) {
                LOGGER.info("No active OptionChainService for order: " + orderId +
                        " - best pair will be used when executing order");
            }
        } catch (Exception e) {
            LOGGER.severe("Error handling BestOptionsUpdatedEvent: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Clean up resources for an order
     * 
     * @param orderId the order ID
     */
    private void cleanupOrder(String orderId) {
        // Remove option chain service
        OptionChainService service = optionChainServices.remove(orderId);
        if (service != null) {
            service.shutdown();
        }

        // Remove best option pair
        bestOptionPairs.remove(orderId);

        // Clear pre-filtered instruments
        orderRepository.clearPreFilteredInstruments(orderId);

        // Remove from tracking maps
        publishedMainOrderEvents.remove(orderId);

        LOGGER.info("Cleaned up resources for order: " + orderId);
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

    /**
     * Helper method to format date-time for logging
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null)
            return "null";
        return dateTime.format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss"));
    }
}