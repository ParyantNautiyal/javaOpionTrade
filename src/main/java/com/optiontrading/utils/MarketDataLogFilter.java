package com.optiontrading.utils;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * Log filter that shows only logs related to market data services
 */
public class MarketDataLogFilter implements Filter {

    @Override
    public boolean isLoggable(LogRecord record) {
        String loggerName = record.getLoggerName();

        // Accept logs from market data related classes
        return loggerName != null && (loggerName.contains("com.optiontrading.service.market") ||
                loggerName.contains("MarketDataService") ||
                loggerName.contains("MarketData") ||
                loggerName.contains("PriceUpdate") ||
                loggerName.contains("QuoteService"));
    }
}