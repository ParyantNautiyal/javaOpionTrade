package com.optiontrading.logging;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Dedicated logger for order-related activities with enhanced detail level
 */
public class OrderLogger {
    private static final Logger LOGGER = Logger.getLogger("com.optiontrading.orders");
    private static boolean initialized = false;

    /**
     * Initialize the order logger with a dedicated log file
     */
    public static void initialize() {
        if (initialized) {
            return;
        }

        try {
            // Create logs directory if it doesn't exist
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            // Configure FileHandler to write to logs/orders.log
            FileHandler fileHandler = new FileHandler("logs/orders.log", true);
            fileHandler.setFormatter(new SimpleFormatter());

            // Set logging level to FINE for more detailed logging
            LOGGER.setLevel(Level.FINE);
            fileHandler.setLevel(Level.FINE);

            // Add the file handler to the logger
            LOGGER.addHandler(fileHandler);

            // Don't pass logs to parent handlers (root logger)
            LOGGER.setUseParentHandlers(false);

            LOGGER.info("Order logging initialized with dedicated log file: logs/orders.log");
            initialized = true;
        } catch (IOException e) {
            System.err.println("Failed to configure order logging: " + e.getMessage());
        }
    }

    /**
     * Log an order-related message at INFO level
     */
    public static void info(String message) {
        if (!initialized) {
            initialize();
        }
        LOGGER.info(message);
    }

    /**
     * Log detailed order information at FINE level
     */
    public static void detail(String message) {
        if (!initialized) {
            initialize();
        }
        LOGGER.fine(message);
    }

    /**
     * Log an order-related warning
     */
    public static void warning(String message) {
        if (!initialized) {
            initialize();
        }
        LOGGER.warning(message);
    }

    /**
     * Log an order-related error
     */
    public static void error(String message, Throwable e) {
        if (!initialized) {
            initialize();
        }
        LOGGER.log(Level.SEVERE, message, e);
    }

    /**
     * Log order creation details
     */
    public static void logOrderCreation(String orderId, String indexSymbol, String expiry,
            String orderType, String executionTime) {
        detail("ORDER CREATED: " + orderId +
                "\n    Index: " + indexSymbol +
                "\n    Expiry: " + expiry +
                "\n    OrderType: " + orderType +
                "\n    ExecutionTime: " + executionTime);
    }

    /**
     * Log order status change
     */
    public static void logStatusChange(String orderId, String oldStatus, String newStatus) {
        info("ORDER STATUS CHANGE: " + orderId + " [" + oldStatus + " -> " + newStatus + "]");
    }

    /**
     * Log instrument filtering details
     */
    public static void logInstrumentFiltering(String orderId, String indexSymbol, String expiry,
            int filteredCount) {
        detail("INSTRUMENT FILTERING: " + orderId +
                "\n    Index: " + indexSymbol +
                "\n    Expiry: " + expiry +
                "\n    Filtered Count: " + filteredCount);
    }

    /**
     * Log order execution attempt
     */
    public static void logExecutionAttempt(String orderId, String stage, String details) {
        info("EXECUTION ATTEMPT: " + orderId + " - " + stage +
                (details != null ? "\n    Details: " + details : ""));
    }
}