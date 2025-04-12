package com.optiontrading.logging;

import java.io.File;
import java.io.IOException;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Dedicated logger for instrument-related activities (downloads, filtering,
 * data caching)
 */
public class InstrumentLogger {
    private static final Logger LOGGER = Logger.getLogger("com.optiontrading.instruments");
    private static boolean initialized = false;

    /**
     * Initialize the instrument logger with a dedicated log file
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

            // Configure FileHandler to write to logs/instruments.log
            FileHandler fileHandler = new FileHandler("logs/instruments.log", true);
            fileHandler.setFormatter(new SimpleFormatter());

            // Set logging level to FINE for more detailed logging
            LOGGER.setLevel(Level.FINE);
            fileHandler.setLevel(Level.FINE);

            // Add the file handler to the logger
            LOGGER.addHandler(fileHandler);

            // Don't pass logs to parent handlers (root logger)
            LOGGER.setUseParentHandlers(false);

            LOGGER.info("Instrument logging initialized with dedicated log file: logs/instruments.log");
            initialized = true;
        } catch (IOException e) {
            System.err.println("Failed to configure instrument logging: " + e.getMessage());
        }
    }

    /**
     * Log an instrument-related message at INFO level
     */
    public static void info(String message) {
        if (!initialized) {
            initialize();
        }
        LOGGER.info(message);
    }

    /**
     * Log detailed instrument information at FINE level
     */
    public static void detail(String message) {
        if (!initialized) {
            initialize();
        }
        LOGGER.fine(message);
    }

    /**
     * Log an instrument-related warning
     */
    public static void warning(String message) {
        if (!initialized) {
            initialize();
        }
        LOGGER.warning(message);
    }

    /**
     * Log an instrument-related error
     */
    public static void error(String message, Throwable e) {
        if (!initialized) {
            initialize();
        }
        LOGGER.log(Level.SEVERE, message, e);
    }

    /**
     * Log the start of instrument download
     */
    public static void logDownloadStart() {
        info("INSTRUMENTS DOWNLOAD STARTED - Fetching from API");
    }

    /**
     * Log instrument download completion
     */
    public static void logDownloadComplete(int count) {
        info("INSTRUMENTS DOWNLOAD COMPLETED - Fetched " + count + " instruments");
    }

    /**
     * Log the start of instrument filtering
     */
    public static void logFilteringStart(String criteria) {
        info("INSTRUMENT FILTERING STARTED - Criteria: " + criteria);
    }

    /**
     * Log instrument filtering results
     */
    public static void logFilteringResults(String criteria, int originalCount, int filteredCount) {
        detail("FILTERING RESULTS - Criteria: " + criteria +
                ", Original count: " + originalCount +
                ", Filtered count: " + filteredCount);
    }

    /**
     * Log file operations
     */
    public static void logFileOperation(String operation, String path, boolean success) {
        if (success) {
            detail("FILE OPERATION: " + operation + " - Path: " + path + " - SUCCESS");
        } else {
            warning("FILE OPERATION: " + operation + " - Path: " + path + " - FAILED");
        }
    }

    /**
     * Log directory creation
     */
    public static void logDirectoryCreation(String path, boolean created) {
        if (created) {
            detail("DIRECTORY CREATED: " + path);
        } else {
            warning("DIRECTORY CREATION FAILED: " + path);
        }
    }
}