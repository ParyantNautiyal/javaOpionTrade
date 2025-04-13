package com.optiontrading.logging;

import com.optiontrading.utils.AppStartupLogFilter;
import com.optiontrading.utils.MainLogFilter;
import com.optiontrading.utils.MarketDataLogFilter;
import com.optiontrading.utils.OrderSchedulerLogFilter;
import com.optiontrading.utils.PositionMonitorLogFilter;

import java.io.File;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Filter;
import java.util.logging.Formatter;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Central logging configuration class that allows switching between different
 * logging modes
 */
public class LoggingConfigurator {

    private static final Logger LOGGER = Logger.getLogger(LoggingConfigurator.class.getName());
    private static final String LOG_DIR = "logs";

    // Available logging modes
    public enum LoggingMode {
        STARTUP, // Focus on application startup and initialization logs
        ORDER_SCHEDULER, // Focus on order scheduling components
        POSITION_MONITOR, // Focus on position monitoring components
        OPTION_CHAIN, // Focus on option chain calculations
        MARKET_DATA, // Focus on market data and price updates
        FULL // Show all logs
    }

    // Current active logging mode
    private static LoggingMode currentMode = LoggingMode.FULL;

    // Active file handler
    private static FileHandler activeFileHandler;
    private static ConsoleHandler activeConsoleHandler;

    /**
     * Configure logging to focus on a specific application area
     * 
     * @param mode The logging mode to activate
     */
    public static void configureLogging(LoggingMode mode) {
        try {
            // Create logs directory if it doesn't exist
            File logDir = new File(LOG_DIR);
            if (!logDir.exists()) {
                boolean created = logDir.mkdirs();
                if (created) {
                    LOGGER.info("Created logs directory");
                }
            }

            // Define the log file name based on the mode
            String logFileName = String.format("logs/%s.log", mode.name().toLowerCase());

            // Clean up any existing handlers
            cleanupExistingHandlers();

            // Create new handlers with the appropriate filter
            Filter filter = createFilterForMode(mode);

            // Create and configure the file handler
            activeFileHandler = new FileHandler(logFileName, 1_000_000, 3, true);
            activeFileHandler.setFormatter(new DetailedLogFormatter());
            if (filter != null) {
                activeFileHandler.setFilter(filter);
            }

            // Create and configure the console handler
            activeConsoleHandler = new ConsoleHandler();
            activeConsoleHandler.setFormatter(new CompactLogFormatter());
            if (filter != null) {
                activeConsoleHandler.setFilter(filter);
            }

            // Get the root logger and configure it
            Logger rootLogger = Logger.getLogger("");
            rootLogger.setLevel(Level.INFO);
            rootLogger.addHandler(activeFileHandler);
            rootLogger.addHandler(activeConsoleHandler);

            // Update the current mode
            currentMode = mode;

            // Log the mode change
            LOGGER.info("Logging configured for mode: " + mode + " to file: " + logFileName);

        } catch (IOException e) {
            System.err.println("Failed to configure logging: " + e.getMessage());
            e.printStackTrace();
        }
        // At the end of configureLogging method:
        Logger.getLogger(LoggingConfigurator.class.getName())
                .info("Initialized " + mode.name() + " logging mode.");
    }

    /**
     * Create a filter based on the selected logging mode
     */
    private static Filter createFilterForMode(LoggingMode mode) {
        switch (mode) {
            case STARTUP:
                return new AppStartupLogFilter();
            case ORDER_SCHEDULER:
                return new OrderSchedulerLogFilter();
            case POSITION_MONITOR:
                return new PositionMonitorLogFilter();
            case OPTION_CHAIN:
                return new MainLogFilter();
            case MARKET_DATA:
                return new MarketDataLogFilter();
            case FULL:
                return null; // No filter means show all logs
            default:
                return null;
        }
    }

    /**
     * Clean up existing handlers to avoid duplication
     */
    private static void cleanupExistingHandlers() {
        // Close the active file handler if it exists
        if (activeFileHandler != null) {
            activeFileHandler.close();
        }

        // Remove existing handlers from the root logger
        Logger rootLogger = Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            rootLogger.removeHandler(handler);
        }
    }

    /**
     * Shutdown logging and close all handlers
     */
    public static void shutdown() {
        LOGGER.info("Shutting down logging system");
        cleanupExistingHandlers();
    }

    /**
     * Get the current logging mode
     */
    public static LoggingMode getCurrentMode() {
        return currentMode;
    }

    /**
     * Custom formatter for detailed log file entries
     */
    static class DetailedLogFormatter extends Formatter {
        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();
            sb.append(DATE_FORMAT.format(new Date(record.getMillis())))
                    .append(" [").append(record.getThreadID()).append("] ")
                    .append(record.getLevel()).append(" ")
                    .append(record.getLoggerName()).append(" - ")
                    .append(formatMessage(record)).append("\n");

            if (record.getThrown() != null) {
                try {
                    sb.append(record.getThrown().getMessage()).append("\n");
                    for (StackTraceElement element : record.getThrown().getStackTrace()) {
                        sb.append("\tat ").append(element).append("\n");
                    }
                } catch (Exception ex) {
                    sb.append("Error formatting exception: ").append(ex.getMessage()).append("\n");
                }
            }

            return sb.toString();
        }
    }

    /**
     * Custom formatter for compact console output
     */
    static class CompactLogFormatter extends Formatter {
        private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("HH:mm:ss");

        @Override
        public String format(LogRecord record) {
            StringBuilder sb = new StringBuilder();

            // Extract simple class name from logger name
            String loggerName = record.getLoggerName();
            String simpleLogger = loggerName;
            int lastDot = loggerName.lastIndexOf('.');
            if (lastDot > 0 && lastDot < loggerName.length() - 1) {
                simpleLogger = loggerName.substring(lastDot + 1);
            }

            // Format as: [TIME] LEVEL [SimpleClassName] Message
            sb.append("[")
                    .append(DATE_FORMAT.format(new Date(record.getMillis())))
                    .append("] ")
                    .append(record.getLevel().getName().charAt(0))
                    .append(" [")
                    .append(simpleLogger)
                    .append("] ")
                    .append(formatMessage(record))
                    .append("\n");

            return sb.toString();
        }
    }
}