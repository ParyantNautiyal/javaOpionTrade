package com.optiontrading.resources;

import com.optiontrading.service.position.PositionRepository;
import com.optiontrading.service.position.PositionWatchlistService;
import com.google.inject.Inject;

import java.util.logging.Logger;
import java.util.logging.Level;

/**
 * Manages shared application resources like thread pools
 */
public class ResourceManager {
    private static final Logger LOGGER = Logger.getLogger(ResourceManager.class.getName());

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

    // Flag to track initialization state
    private boolean fullyInitialized = false;

    /**
     * Constructor with dependency injection
     */
    @Inject
    public ResourceManager(ThreadManager threadManager,
            TimerManager timerManager,
            CacheManager cacheManager,
            PositionRepository positionRepository,
            PositionWatchlistService positionWatchlistService) {
        LOGGER.info("Starting ResourceManager initialization with dependency injection");

        this.threadManager = threadManager;
        this.timerManager = timerManager;
        this.cacheManager = cacheManager;
        this.positionRepository = positionRepository;
        this.positionWatchlistService = positionWatchlistService;

        // Mark as initialized
        this.fullyInitialized = true;

        LOGGER.info("ResourceManager initialized");
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
     * Check if the resource manager is fully initialized
     */
    public boolean isInitialized() {
        return fullyInitialized;
    }

    /**
     * Shutdown all managed resources
     */
    public void shutdown() {
        LOGGER.info("Shutting down resources");

        try {
            // Shut down position repository
            if (positionRepository != null) {
                positionRepository.shutdown();
            }

            // Shutdown position watchlist
            if (positionWatchlistService != null) {
                positionWatchlistService.shutdown();
            }

            // Shutdown thread pools
            if (threadManager != null) {
                threadManager.shutdownAllExecutors();
            }

            // Cancel all timers
            if (timerManager != null) {
                timerManager.shutdownAllTimers();
            }

            // Clear caches
            if (cacheManager != null) {
                cacheManager.shutdown();
            }

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

        if (positionWatchlistService != null) {
            metrics.append("--- POSITION WATCHLIST METRICS ---\n");
            metrics.append("Active positions: ").append(positionWatchlistService.getActivePositions().size())
                    .append("\n");
            metrics.append("Total positions: ").append(positionWatchlistService.getAllPositions().size()).append("\n");
        }

        return metrics.toString();
    }
}