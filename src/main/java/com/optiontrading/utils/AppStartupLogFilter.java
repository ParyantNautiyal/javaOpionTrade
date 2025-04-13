package com.optiontrading.utils;

import java.util.logging.Filter;
import java.util.logging.LogRecord;

/**
 * Log filter that focuses only on application startup and initialization
 * related classes and packages.
 */
public class AppStartupLogFilter implements Filter {

    // List of packages to include during startup logging
    private static final String[] STARTUP_PACKAGES = {
            "com.optiontrading.Main",
            "com.optiontrading.di",
            "com.optiontrading.resources.ResourceManager",
            "com.optiontrading.service.auth"
    };

    @Override
    public boolean isLoggable(LogRecord record) {
        // Always include severe logs
        if (record.getLevel().intValue() >= java.util.logging.Level.SEVERE.intValue()) {
            return true;
        }

        // Check if the logger name is related to startup
        String loggerName = record.getLoggerName();
        if (loggerName != null) {
            for (String pkg : STARTUP_PACKAGES) {
                if (loggerName.startsWith(pkg)) {
                    return true;
                }
            }
        }

        // By default, exclude logs not related to startup
        return false;
    }
}