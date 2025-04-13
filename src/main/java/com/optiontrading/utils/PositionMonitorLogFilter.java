package com.optiontrading.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * Log filter to isolate Position Monitor related logs
 */
public class PositionMonitorLogFilter implements Filter {

    // Components involved in position monitoring
    private static final Set<String> POSITION_COMPONENTS = new HashSet<>(Arrays.asList(
            "com.optiontrading.service.position.PositionWatchlistService",
            "com.optiontrading.service.position.PositionRepository",
            "com.optiontrading.ui.panels.PositionMonitorPanel"));

    // Keywords indicating position monitoring activities
    private static final Set<String> POSITION_KEYWORDS = new HashSet<>(Arrays.asList(
            "position",
            "monitoring",
            "watched",
            "trigger",
            "stop loss",
            "price",
            "updated",
            "added",
            "removed",
            "executed"));

    @Override
    public boolean isLoggable(LogRecord record) {
        if (record == null || record.getLoggerName() == null) {
            return false;
        }

        String loggerName = record.getLoggerName();

        // Check if the logger is from position monitoring components
        for (String component : POSITION_COMPONENTS) {
            if (loggerName.equals(component) || loggerName.startsWith(component)) {
                // If it's from a position component, check for relevant keywords
                String message = record.getMessage();
                if (message != null) {
                    message = message.toLowerCase();
                    for (String keyword : POSITION_KEYWORDS) {
                        if (message.contains(keyword.toLowerCase())) {
                            return true;
                        }
                    }
                }

                // If it's from these components but doesn't have keywords, still log it
                // as most messages from these components would be relevant
                return true;
            }
        }

        return false;
    }
}