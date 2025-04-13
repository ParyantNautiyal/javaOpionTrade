package com.optiontrading.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * Log filter to isolate OrderScheduler related logs
 */
public class OrderSchedulerLogFilter implements Filter {

    // Components involved in order scheduling
    private static final Set<String> ORDER_COMPONENTS = new HashSet<>(Arrays.asList(
            "com.optiontrading.service.order.OrderExecutionCoordinator",
            "com.optiontrading.service.order.OrderRepository",
            "com.optiontrading.service.order.OrderScheduler"));

    // Keywords indicating order scheduling activities
    private static final Set<String> ORDER_KEYWORDS = new HashSet<>(Arrays.asList(
            "scheduling",
            "scheduled",
            "executing",
            "order",
            "execution",
            "preparation",
            "preparing",
            "task",
            "timer"));

    @Override
    public boolean isLoggable(LogRecord record) {
        if (record == null || record.getLoggerName() == null) {
            return false;
        }

        String loggerName = record.getLoggerName();

        // Check if the logger is from order scheduling components
        for (String component : ORDER_COMPONENTS) {
            if (loggerName.equals(component) || loggerName.startsWith(component)) {
                // If it's from an order component, check for relevant keywords
                String message = record.getMessage();
                if (message != null) {
                    message = message.toLowerCase();
                    for (String keyword : ORDER_KEYWORDS) {
                        if (message.contains(keyword)) {
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