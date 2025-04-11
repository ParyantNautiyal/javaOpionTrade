package com.optiontrading.resources;

import java.util.logging.Logger;

/**
 * Manages shared application resources like thread pools
 */
public class ResourceManager {
    private static final Logger LOGGER = Logger.getLogger(ResourceManager.class.getName());
    private static final ResourceManager INSTANCE = new ResourceManager();

    // Thread manager
    private final ThreadManager threadManager;

    // Timer manager
    private final TimerManager timerManager;

    // Cache manager
    private final CacheManager cacheManager;

    // Private constructor for singleton
    private ResourceManager() {
        this.threadManager = ThreadManager.getInstance();
        this.timerManager = TimerManager.getInstance();
        this.cacheManager = CacheManager.getInstance();

        LOGGER.info("ResourceManager initialized");
    }

    /**
     * Get the singleton instance
     */
    public static ResourceManager getInstance() {
        return INSTANCE;
    }

    /**
     * Get the thread manager
     */
    public ThreadManager getThreadManager() {
        return threadManager;
    }

    /**
     * Get the timer manager
     */
    public TimerManager getTimerManager() {
        return timerManager;
    }

    /**
     * Get the cache manager
     */
    public CacheManager getCacheManager() {
        return cacheManager;
    }

    /**
     * Shutdown all managed resources
     */
    public void shutdown() {
        LOGGER.info("Shutting down ResourceManager");

        // Shutdown thread pools
        threadManager.shutdownAllExecutors();

        // Cancel all timers
        timerManager.shutdownAllTimers();

        // Clear caches
        cacheManager.shutdown();

        LOGGER.info("ResourceManager shutdown complete");
    }

    /**
     * Get metrics about all managed resources
     * 
     * @return String representation of resource metrics
     */
    public String getResourceMetrics() {
        StringBuilder metrics = new StringBuilder();
        metrics.append("===== RESOURCE MANAGER METRICS =====\n");

        metrics.append("--- THREAD METRICS ---\n");
        metrics.append(threadManager.getExecutorMetrics());

        metrics.append("--- TIMER METRICS ---\n");
        metrics.append("Active timers: ").append(timerManager.getActiveTimerCount()).append("\n");

        metrics.append("--- CACHE METRICS ---\n");
        metrics.append("Active caches: ").append(cacheManager.getCacheCount()).append("\n");

        return metrics.toString();
    }
}