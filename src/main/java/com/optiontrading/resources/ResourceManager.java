package com.optiontrading.resources;

import com.optiontrading.service.position.PositionRepository;
import com.optiontrading.service.position.PositionWatchlistService;

import java.util.logging.Logger;
import java.util.logging.Level;

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

    // Position watchlist service
    private final PositionWatchlistService positionWatchlistService;

    // Position repository
    private final PositionRepository positionRepository;

    // Private constructor for singleton
    private ResourceManager() {
        this.threadManager = ThreadManager.getInstance();
        this.timerManager = TimerManager.getInstance();
        this.cacheManager = CacheManager.getInstance();
        this.positionWatchlistService = PositionWatchlistService.getInstance();
        this.positionRepository = PositionRepository.getInstance();

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
     * Get the position watchlist service
     */
    public PositionWatchlistService getPositionWatchlistService() {
        return positionWatchlistService;
    }

    /**
     * Get the position repository
     */
    public PositionRepository getPositionRepository() {
        return positionRepository;
    }

    /**
     * Shutdown all managed resources
     */
    public void shutdown() {
        LOGGER.info("Shutting down resources");

        try {
            // Shut down position repository
            PositionRepository.getInstance().shutdown();

            // Shutdown thread pools
            threadManager.shutdownAllExecutors();

            // Cancel all timers
            timerManager.shutdownAllTimers();

            // Clear caches
            cacheManager.shutdown();

            // Shutdown position watchlist
            positionWatchlistService.shutdown();

            LOGGER.info("ResourceManager shutdown complete");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during resource shutdown", e);
        }
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

        metrics.append("--- POSITION WATCHLIST METRICS ---\n");
        metrics.append("Active positions: ").append(positionWatchlistService.getActivePositions().size()).append("\n");
        metrics.append("Total positions: ").append(positionWatchlistService.getAllPositions().size()).append("\n");

        return metrics.toString();
    }
}