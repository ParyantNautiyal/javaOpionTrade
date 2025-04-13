package com.optiontrading.utils;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * Filter for the main app.log to only show option chain calculations
 */
public class MainLogFilter implements Filter {

    // Only show logs from OptionChainService
    private static final String TARGET_LOGGER = "com.optiontrading.service.option.OptionChainService";

    @Override
    public boolean isLoggable(LogRecord record) {
        // Allow logs from the OptionChainService
        if (record.getLoggerName().equals(TARGET_LOGGER)) {
            return true;
        }

        // Block all other logs
        return false;
    }
}