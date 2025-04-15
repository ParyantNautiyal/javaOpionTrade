package com.optiontrading.utils;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;

/**
 * Specialized logger for option trading operations
 * Modified to minimize logging output
 */
public class TradeLogManager {

    private static final String LOG_FOLDER = "logs";
    private static final String TRADE_LOG_FILE = "optionTrades.log";
    private static FileHandler tradeFileHandler;
    private static final Logger LOGGER = Logger.getLogger(TradeLogManager.class.getName());

    // Minimal set of loggers to monitor - only the most critical service
    private static final String[] MONITORED_LOGGERS = {
            "com.optiontrading.service.order.OrderExecutionCoordinator"
    };

    // Track if initialized to prevent duplicate handlers
    private static boolean initialized = false;

    /**
     * Initialize the specialized option calculation and order placement logger
     * with minimal logging enabled
     */
    public static void initialize() {
        if (initialized) {
            LOGGER.info("Trade logging already initialized");
            return;
        }

        try {
            // Create logs directory if it doesn't exist
            File logDir = new File(LOG_FOLDER);
            if (!logDir.exists()) {
                LOGGER.info("Creating logs directory: " + logDir.getAbsolutePath());
                if (logDir.mkdirs()) {
                    LOGGER.info("Created logs directory successfully");
                } else {
                    LOGGER.warning("Failed to create logs directory");
                }
            }

            // Create log file if it doesn't exist
            String logPath = LOG_FOLDER + File.separator + TRADE_LOG_FILE;
            File logFile = new File(logPath);
            if (!logFile.exists()) {
                try {
                    if (logFile.createNewFile()) {
                        LOGGER.info("Created new log file: " + logFile.getAbsolutePath());
                        // Write an initial entry
                        try (FileWriter writer = new FileWriter(logFile)) {
                            writer.write("=== Option Trading Log File Created: " +
                                    new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " ===\n");
                        }
                    }
                } catch (IOException e) {
                    LOGGER.log(Level.WARNING, "Failed to create log file", e);
                }
            } else {
                // Clear existing file for a fresh start
                try (FileWriter writer = new FileWriter(logFile, false)) {
                    writer.write("=== Log File Reset: " +
                            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()) + " ===\n");
                } catch (IOException e) {
                    LOGGER.warning("Failed to reset log file: " + e.getMessage());
                }
            }

            // Configure the file handler with minimal logging
            try {
                tradeFileHandler = new FileHandler(logPath, 512000, 2, true); // 512KB file, 2 files max (reduced size)
                tradeFileHandler.setFormatter(new TradeLogFormatter());

                // Only log WARNING or higher - significantly reduces log volume
                tradeFileHandler.setLevel(Level.WARNING);

                // Add handler only to monitored loggers and prevent propagation
                for (String loggerName : MONITORED_LOGGERS) {
                    Logger logger = Logger.getLogger(loggerName);

                    // Remove any existing handlers of the same type to avoid duplicates
                    removeExistingHandlers(logger);

                    // Prevent propagation to parent loggers
                    logger.setUseParentHandlers(false);

                    // Add our focused handler
                    logger.addHandler(tradeFileHandler);
                    LOGGER.info("Added handler to " + loggerName);
                }

                // Mark as initialized
                initialized = true;

                // Log a startup message to the file
                logTradeEvent("Option trading logging activated - minimal logging mode");

                LOGGER.info("Trade logging initialized successfully. Using file: " + logFile.getAbsolutePath());
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error setting up log file handler", e);
            }
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Failed to initialize trade logging", e);
        }
    }

    /**
     * Remove any existing file handlers to prevent duplicates
     */
    private static void removeExistingHandlers(Logger logger) {
        java.util.logging.Handler[] handlers = logger.getHandlers();
        for (java.util.logging.Handler handler : handlers) {
            if (handler instanceof FileHandler) {
                logger.removeHandler(handler);
            }
        }
    }

    /**
     * Log a critical trading event - use this only for important events
     * 
     * @param message The message to log
     */
    public static void logTradeEvent(String message) {
        // Only log the event if it's truly important
        if (message != null &&
                (message.contains("ERROR") ||
                        message.contains("FAILED") ||
                        message.contains("EXECUTED") ||
                        message.contains("COMPLETED"))) {
            Logger.getLogger("com.optiontrading.TradeEvents").warning(message);
        }
    }

    /**
     * Custom formatter for trade logs - simplified to reduce log size
     */
    static class TradeLogFormatter extends Formatter {
        private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();

            // Simplified format: [time] [level] [source] message
            sb.append("[")
                    .append(dateFormat.format(new Date(record.getMillis())))
                    .append("] [")
                    .append(record.getLevel().getName())
                    .append("] ")
                    .append(formatMessage(record))
                    .append("\n");

            return sb.toString();
        }
    }

    /**
     * Shutdown the trade logger
     */
    public static void shutdown() {
        if (tradeFileHandler != null) {
            tradeFileHandler.close();
            initialized = false;
        }
    }
}