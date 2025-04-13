package com.optiontrading.utils;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;
import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * Strictly focused filter for option chain calculations and order placement
 */
public class TradeLogFilter implements Filter {

    // Only focus on these two core services
    private static final Set<String> CRITICAL_SERVICES = new HashSet<>(Arrays.asList(
            "com.optiontrading.service.option.OptionChainService",
            "com.optiontrading.service.order.OrderExecutionCoordinator"));

    // Very specific phrases that indicate important calculation and order events
    private static final Set<String> CRITICAL_PHRASES = new HashSet<>(Arrays.asList(
            // Option chain calculation specific phrases
            "new best pair found",
            "finding best option pair",
            "updated best option pair",
            "best difference",

            // Order placement specific phrases
            "executing main order",
            "main order placed",
            "publishing mainorderplacedevent",
            "mainorderplacedevent published"));

    @Override
    public boolean isLoggable(LogRecord record) {
        // Always allow manual trade event logs
        if (record.getLoggerName().equals("com.optiontrading.TradeEvents")) {
            return true;
        }

        // First check if it's from a critical service
        String loggerName = record.getLoggerName();
        boolean isFromCriticalService = false;

        for (String service : CRITICAL_SERVICES) {
            if (loggerName.startsWith(service)) {
                isFromCriticalService = true;
                break;
            }
        }

        if (!isFromCriticalService) {
            return false;
        }

        // Then check if the message contains a critical phrase
        String message = record.getMessage();
        if (message == null) {
            return false;
        }

        message = message.toLowerCase();
        for (String phrase : CRITICAL_PHRASES) {
            if (message.contains(phrase.toLowerCase())) {
                return true;
            }
        }

        // Reject all other logs
        return false;
    }
}