package com.optiontrading.service.order;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.service.model.OptionPair;
import com.optiontrading.service.model.OrderStatus;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.option.BestOptionsUpdatedEvent;
import com.optiontrading.service.trading.OrderFailedEvent;
import com.optiontrading.service.trading.OrderPlacedEvent;
import com.optiontrading.service.model.OrderType;
import com.optiontrading.service.model.Instrument;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.math.BigDecimal;
import java.text.SimpleDateFormat;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Service for logging order-related activities to a dedicated file
 */
@Singleton
public class OrderLoggerService {
    private static final String LOG_DIR = "logs";
    private static final String ORDER_LOG_FILE = LOG_DIR + "/order_lifecycle.log";
    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSS");

    private final PrintWriter logWriter;
    private final ConcurrentHashMap<String, StringBuilder> orderHistoryMap = new ConcurrentHashMap<>();

    /**
     * Constructor with dependency injection
     */
    @Inject
    public OrderLoggerService(EventBus eventBus) {
        try {
            // Create log directory if it doesn't exist
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            // Create or append to log file
            logWriter = new PrintWriter(new FileWriter(ORDER_LOG_FILE, true), true);
            logWriter.println("\n==== ORDER LOGGER INITIALIZED: "
                    + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " ====");

            // Subscribe to order-related events
            subscribeToEvents(eventBus);

            // Log initialization
            logWriter.println("Order logger initialized successfully");
        } catch (IOException e) {
            throw new RuntimeException("Failed to initialize OrderLoggerService: " + e.getMessage(), e);
        }
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
     * Log an order creation
     */
    public void logOrderCreated(ScheduledOrder order) {
        String orderId = order.getOrderId();
        String message = String.format(
                "ORDER CREATED - ID: %s, Index: %s, Type: %s, Lots: %d, Execution Time: %s",
                orderId,
                order.getParams().getIndexSymbol(),
                order.getParams().getOrderType(),
                order.getParams().getLots(),
                formatDateTime(order.getExecutionTime()));

        logMessage(orderId, message);

        // Log all detailed parameters
        StringBuilder detailedParams = new StringBuilder();
        detailedParams.append("\n====== ORDER PARAMETERS DETAILS ======\n");
        detailedParams.append("Order ID: ").append(orderId).append("\n");
        detailedParams.append("Created at: ").append(formatDateTime(order.getCreatedAt())).append("\n");
        detailedParams.append("Index Symbol: ").append(order.getParams().getIndexSymbol()).append("\n");
        detailedParams.append("Order Type: ").append(order.getParams().getOrderType()).append("\n");
        detailedParams.append("Order Direction: ")
                .append(order.getParams().getOrderType() == OrderType.BUY ? "LONG" : "SHORT").append("\n");
        detailedParams.append("Quantity (Lots): ").append(order.getParams().getLots()).append("\n");
        detailedParams.append("Execution Time: ").append(formatDateTime(order.getExecutionTime())).append("\n");
        detailedParams.append("Expiry Date: ").append(order.getParams().getExpiryDate()).append("\n");
        detailedParams.append("Target Premium: ").append(order.getParams().getTargetPremium()).append("\n");

        // Risk management parameters
        detailedParams.append("\n----- Risk Management Parameters -----\n");
        detailedParams.append("Stop Loss Enabled: ").append(order.getParams().isStopLossEnabled()).append("\n");
        if (order.getParams().isStopLossEnabled()) {
            detailedParams.append("Threshold/Stop Level: ").append(order.getParams().getThreshold()).append("%\n");
            detailedParams.append("Move SL to Cost: ").append(order.getParams().isMoveSlToCost()).append("\n");
            detailedParams.append("Trailing SL: ").append(order.getParams().isTrailingSl()).append("\n");
        }

        // Strategy parameters
        detailedParams.append("\n----- Strategy Specifications -----\n");
        detailedParams.append("Hedging Enabled: ").append(order.getParams().isHedgingEnabled()).append("\n");
        if (order.getParams().isHedgingEnabled()) {
            detailedParams.append("Hedge Point Difference: ").append(order.getParams().getHedgePointDifference())
                    .append("\n");
        }
        detailedParams.append("Threshold: ").append(order.getParams().getThreshold()).append("%\n");

        // Additional parameters could be added here if they're available in
        // OrderScheduleParams

        detailedParams.append("======================================\n");

        // Log the detailed parameters to the file
        logWriter
                .println(formatDateTime(LocalDateTime.now()) + " [ORDER:" + orderId + "] " + detailedParams.toString());

        // Initialize history for this order
        StringBuilder history = new StringBuilder();
        history.append("Order created: ").append(formatDateTime(LocalDateTime.now())).append("\n");
        history.append("Scheduled execution: ").append(formatDateTime(order.getExecutionTime())).append("\n");
        history.append("Index: ").append(order.getParams().getIndexSymbol()).append("\n");
        history.append("Type: ").append(order.getParams().getOrderType()).append("\n");
        history.append("Lots: ").append(order.getParams().getLots()).append("\n");
        history.append("Detailed parameters logged to file.\n");

        orderHistoryMap.put(orderId, history);
    }

    /**
     * Log best options selected for an order
     */
    public void logBestOptionsSelected(String orderId, OptionPair optionPair) {
        if (optionPair == null) {
            logMessage(orderId, "BEST OPTIONS - No suitable options found");
            appendToHistory(orderId, "No suitable options found");
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

        logMessage(orderId, message);

        // Enhanced detailed logging of selected options
        StringBuilder optionsDetails = new StringBuilder();
        optionsDetails.append("\n====== SELECTED OPTIONS DETAILS ======\n");

        // Call option details
        if (optionPair.hasCallOption()) {
            Instrument callOption = optionPair.getCallOption();
            optionsDetails.append("----- CALL OPTION DETAILS -----\n");
            optionsDetails.append("Symbol: ").append(callOption.getTradingSymbol()).append("\n");
            optionsDetails.append("Exchange: ").append(callOption.getExchange()).append("\n");
            optionsDetails.append("Instrument ID: ").append(callOption.getInstrumentId()).append("\n");
            optionsDetails.append("Strike Price: ").append(callOption.getStrikePrice()).append("\n");
            optionsDetails.append("Option Type: ").append(callOption.getOptionType()).append("\n");
            optionsDetails.append("Underlying: ").append(callOption.getUnderlyingSymbol()).append("\n");
            optionsDetails.append("Price: ").append(optionPair.getCallPrice()).append("\n");
            optionsDetails.append("Lot Size: ").append(callOption.getLotSize()).append("\n");
        } else {
            optionsDetails.append("----- NO CALL OPTION SELECTED -----\n");
        }

        // Put option details
        if (optionPair.hasPutOption()) {
            Instrument putOption = optionPair.getPutOption();
            optionsDetails.append("\n----- PUT OPTION DETAILS -----\n");
            optionsDetails.append("Symbol: ").append(putOption.getTradingSymbol()).append("\n");
            optionsDetails.append("Exchange: ").append(putOption.getExchange()).append("\n");
            optionsDetails.append("Instrument ID: ").append(putOption.getInstrumentId()).append("\n");
            optionsDetails.append("Strike Price: ").append(putOption.getStrikePrice()).append("\n");
            optionsDetails.append("Option Type: ").append(putOption.getOptionType()).append("\n");
            optionsDetails.append("Underlying: ").append(putOption.getUnderlyingSymbol()).append("\n");
            optionsDetails.append("Price: ").append(optionPair.getPutPrice()).append("\n");
            optionsDetails.append("Lot Size: ").append(putOption.getLotSize()).append("\n");
        } else {
            optionsDetails.append("\n----- NO PUT OPTION SELECTED -----\n");
        }

        optionsDetails.append("\n----- COMBINED VALUES -----\n");
        optionsDetails.append("Combined Premium: ").append(getCombinedPremium(optionPair)).append("\n");
        optionsDetails.append("======================================\n");

        // Log the detailed parameters to the file
        logWriter
                .println(formatDateTime(LocalDateTime.now()) + " [ORDER:" + orderId + "] " + optionsDetails.toString());

        // Append to order history
        String historyEntry = String.format("Best options selected: %s%s",
                optionPair.hasCallOption() ? "Call: " + optionPair.getCallOption().getTradingSymbol() + " " : "",
                optionPair.hasPutOption() ? "Put: " + optionPair.getPutOption().getTradingSymbol() : "");
        appendToHistory(orderId, historyEntry);
    }

    /**
     * Log hedge orders placed
     */
    public void logHedgeOrdersPlaced(String orderId, OptionPair optionPair) {
        String message = String.format(
                "HEDGE ORDERS PLACED - ID: %s, Call Hedge: %s, Put Hedge: %s, Options ready for execution",
                orderId,
                optionPair.hasCallOption() ? optionPair.getCallOption().getTradingSymbol() : "N/A",
                optionPair.hasPutOption() ? optionPair.getPutOption().getTradingSymbol() : "N/A");

        logMessage(orderId, message);

        // Add detailed hedge order information
        StringBuilder hedgeDetails = new StringBuilder();
        hedgeDetails.append("\n====== HEDGE ORDERS DETAILS ======\n");

        // Call option details
        if (optionPair.hasCallOption()) {
            Instrument callOption = optionPair.getCallOption();
            hedgeDetails.append("----- CALL HEDGE DETAILS -----\n");
            hedgeDetails.append("Symbol: ").append(callOption.getTradingSymbol()).append("\n");
            hedgeDetails.append("Strike Price: ").append(callOption.getStrikePrice()).append("\n");
            hedgeDetails.append("Price: ").append(optionPair.getCallPrice()).append("\n");
            hedgeDetails.append("Quantity: ").append(callOption.getLotSize()).append("\n");
        } else {
            hedgeDetails.append("----- NO CALL HEDGE PLACED -----\n");
        }

        // Put option details
        if (optionPair.hasPutOption()) {
            Instrument putOption = optionPair.getPutOption();
            hedgeDetails.append("\n----- PUT HEDGE DETAILS -----\n");
            hedgeDetails.append("Symbol: ").append(putOption.getTradingSymbol()).append("\n");
            hedgeDetails.append("Strike Price: ").append(putOption.getStrikePrice()).append("\n");
            hedgeDetails.append("Price: ").append(optionPair.getPutPrice()).append("\n");
            hedgeDetails.append("Quantity: ").append(putOption.getLotSize()).append("\n");
        } else {
            hedgeDetails.append("\n----- NO PUT HEDGE PLACED -----\n");
        }

        hedgeDetails.append("======================================\n");

        // Log the detailed hedge parameters to the file
        logWriter.println(formatDateTime(LocalDateTime.now()) + " [ORDER:" + orderId + "] " + hedgeDetails.toString());

        StringBuilder historyEntry = new StringBuilder();
        historyEntry.append("Hedge orders placed at: ").append(formatDateTime(LocalDateTime.now())).append("\n");
        if (optionPair.hasCallOption()) {
            historyEntry.append("  Call Hedge: ").append(optionPair.getCallOption().getTradingSymbol())
                    .append(" @ ").append(optionPair.getCallPrice()).append("\n");
        }
        if (optionPair.hasPutOption()) {
            historyEntry.append("  Put Hedge: ").append(optionPair.getPutOption().getTradingSymbol())
                    .append(" @ ").append(optionPair.getPutPrice()).append("\n");
        }
        historyEntry.append("Order ready for main execution\n");

        appendToHistory(orderId, historyEntry.toString());
    }

    /**
     * Log main order placed
     */
    public void logMainOrderPlaced(String orderId, OptionPair optionPair) {
        String message = String.format(
                "MAIN ORDER PLACED - ID: %s, Call: %s, Put: %s, Transaction Complete",
                orderId,
                optionPair.hasCallOption() ? optionPair.getCallOption().getTradingSymbol() : "N/A",
                optionPair.hasPutOption() ? optionPair.getPutOption().getTradingSymbol() : "N/A");

        logMessage(orderId, message);

        StringBuilder historyEntry = new StringBuilder();
        historyEntry.append("Main order completed at: ").append(formatDateTime(LocalDateTime.now())).append("\n");
        historyEntry.append("Transaction complete - position created for monitoring\n");

        appendToHistory(orderId, historyEntry.toString());

        // Complete the history log once the transaction is complete
        StringBuilder fullHistory = orderHistoryMap.get(orderId);
        if (fullHistory != null) {
            logWriter.println("\n==== COMPLETE ORDER HISTORY FOR " + orderId + " ====");
            logWriter.println(fullHistory.toString());
            logWriter.println("==== END OF HISTORY FOR " + orderId + " ====\n");
        }
    }

    /**
     * Log individual order placement
     */
    public void logOrderPlaced(OrderPlacedEvent event) {
        // Extract the order ID from the tag if present (format: "TYPE-orderId")
        String tag = event.getTag();
        String orderId = tag != null && tag.contains("-") ? tag.split("-")[1] : "unknown";

        String message = String.format(
                "ORDER EXECUTION - Type: %s, Instrument: %s, Qty: %d, Price: %s, Broker ID: %s",
                event.getOrderType(),
                event.getInstrument().getTradingSymbol(),
                event.getQuantity(),
                event.getPrice() != null ? event.getPrice().toString() : "Market",
                event.getOrderId());

        logMessage(orderId, message);

        StringBuilder historyEntry = new StringBuilder();
        historyEntry.append("Order executed at: ").append(formatDateTime(LocalDateTime.now())).append("\n");
        historyEntry.append("  Type: ").append(event.getOrderType()).append("\n");
        historyEntry.append("  Instrument: ").append(event.getInstrument().getTradingSymbol()).append("\n");
        historyEntry.append("  Quantity: ").append(event.getQuantity()).append("\n");
        historyEntry.append("  Price: ").append(event.getPrice() != null ? event.getPrice().toString() : "Market")
                .append("\n");
        historyEntry.append("  Broker ID: ").append(event.getOrderId()).append("\n");

        appendToHistory(orderId, historyEntry.toString());
    }

    /**
     * Log order failure
     */
    public void logOrderFailed(OrderFailedEvent event) {
        String orderId = event.getStrategyId();

        String message = String.format(
                "ORDER FAILED - Strategy: %s, Instrument: %s, Type: %s, Error: %s",
                orderId,
                event.getInstrument().getTradingSymbol(),
                event.getOrderType(),
                event.getErrorMessage());

        logMessage(orderId, message);

        // Add detailed failure information
        StringBuilder failureDetails = new StringBuilder();
        failureDetails.append("\n====== ORDER FAILURE DETAILS ======\n");
        failureDetails.append("Failed at: ").append(formatDateTime(LocalDateTime.now())).append("\n");
        failureDetails.append("Order ID: ").append(orderId).append("\n");
        failureDetails.append("Instrument: ").append(event.getInstrument().getTradingSymbol()).append("\n");
        failureDetails.append("Order Type: ").append(event.getOrderType()).append("\n");
        failureDetails.append("Error Message: ").append(event.getErrorMessage()).append("\n");
        failureDetails.append("======================================\n");

        // Log the detailed failure information
        logWriter
                .println(formatDateTime(LocalDateTime.now()) + " [ORDER:" + orderId + "] " + failureDetails.toString());

        StringBuilder historyEntry = new StringBuilder();
        historyEntry.append("Order failed at: ").append(formatDateTime(LocalDateTime.now())).append("\n");
        historyEntry.append("  Instrument: ").append(event.getInstrument().getTradingSymbol()).append("\n");
        historyEntry.append("  Type: ").append(event.getOrderType()).append("\n");
        historyEntry.append("  Error: ").append(event.getErrorMessage()).append("\n");

        appendToHistory(orderId, historyEntry.toString());

        // Log the complete history for failed orders too
        StringBuilder fullHistory = orderHistoryMap.get(orderId);
        if (fullHistory != null) {
            logWriter.println("\n==== COMPLETE ORDER HISTORY FOR FAILED ORDER " + orderId + " ====");
            logWriter.println(fullHistory.toString());
            logWriter.println("==== END OF HISTORY FOR " + orderId + " ====\n");
        }
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

        logMessage(orderId, message);

        StringBuilder historyEntry = new StringBuilder();
        historyEntry.append("Status changed at: ").append(formatDateTime(LocalDateTime.now())).append("\n");
        historyEntry.append("  From: ").append(oldStatus).append("\n");
        historyEntry.append("  To: ").append(newStatus).append("\n");

        appendToHistory(orderId, historyEntry.toString());
    }

    /**
     * Log a generic message for an order
     */
    public void logMessage(String orderId, String message) {
        logWriter.println(formatDateTime(LocalDateTime.now()) + " [ORDER:" + orderId + "] " + message);
    }

    /**
     * Append to the order history
     */
    private void appendToHistory(String orderId, String entry) {
        orderHistoryMap.computeIfAbsent(orderId, k -> new StringBuilder()).append(entry);
    }

    /**
     * Format date time for logging
     */
    private String formatDateTime(LocalDateTime dateTime) {
        return dateTime.format(DATE_TIME_FORMATTER);
    }

    /**
     * Shutdown the logger
     */
    public void shutdown() {
        logWriter.println("OrderLoggerService shutdown at " + formatDateTime(LocalDateTime.now()));
        logWriter.close();
    }

    /**
     * Log order cleanup
     * 
     * @param orderId        The order ID
     * @param cleanupDetails Details about the cleanup
     */
    public void logOrderCleanup(String orderId, String cleanupDetails) {
        String message = String.format("CLEANED UP - %s", cleanupDetails);
        logMessage(orderId, message);

        appendToHistory(orderId, "Order cleaned up: " + cleanupDetails + "\n");

        // Always log the complete history at cleanup for reference
        StringBuilder fullHistory = orderHistoryMap.get(orderId);
        if (fullHistory != null) {
            logWriter.println("\n==== COMPLETE ORDER LIFECYCLE HISTORY FOR " + orderId + " ====");
            logWriter.println(fullHistory.toString());
            logWriter.println("==== END OF LIFECYCLE FOR " + orderId + " ====\n");

            // Remove from memory after logging
            orderHistoryMap.remove(orderId);
        }
    }
}