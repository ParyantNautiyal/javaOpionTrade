package com.optiontrading.service.position;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Repository for persisting positions
 */
@Singleton
public class PositionRepository {
    private static final Logger LOGGER = Logger.getLogger(PositionRepository.class.getName());

    private static final String DATA_DIR = "data/positions";
    private static final String ACTIVE_POSITIONS_FILE = DATA_DIR + "/active_positions.dat";
    private static final String HISTORY_DIR = DATA_DIR + "/history";

    // Cache configuration
    private static final int CACHE_FLUSH_THRESHOLD = 15;
    private static final int CACHE_FLUSH_INTERVAL_MINUTES = 5;

    // Map of position ID to position
    private final Map<String, WatchedPosition> positions = new ConcurrentHashMap<>();

    // Cache of modified positions that need to be written to disk
    private final Set<String> modifiedPositionIds = ConcurrentHashMap.newKeySet();
    private final Set<WatchedPosition> positionsForHistory = ConcurrentHashMap.newKeySet();
    private final AtomicInteger pendingChanges = new AtomicInteger(0);

    // Scheduler for background flushing
    private final ScheduledExecutorService cacheFlushScheduler = Executors.newSingleThreadScheduledExecutor();

    /**
     * Constructor with dependency injection
     */
    @Inject
    public PositionRepository() {
        // Ensure data directories exist
        File dataDir = new File(DATA_DIR);
        if (!dataDir.exists()) {
            dataDir.mkdirs();
        }

        File historyDir = new File(HISTORY_DIR);
        if (!historyDir.exists()) {
            historyDir.mkdirs();
        }

        // Load saved positions
        loadPositions();

        // Schedule periodic cache flush
        cacheFlushScheduler.scheduleAtFixedRate(
                this::flushCache,
                CACHE_FLUSH_INTERVAL_MINUTES,
                CACHE_FLUSH_INTERVAL_MINUTES,
                TimeUnit.MINUTES);

        LOGGER.info("PositionRepository initialized with " + positions.size() +
                " positions and cache flush interval of " + CACHE_FLUSH_INTERVAL_MINUTES + " minutes");
    }

    /**
     * Save a position
     * 
     * @param position the position to save
     */
    public void savePosition(WatchedPosition position) {
        if (position == null) {
            return;
        }

        positions.put(position.getId(), position);

        // Mark position as modified
        modifiedPositionIds.add(position.getId());
        int changes = pendingChanges.incrementAndGet();

        // If position is not active, queue for history
        if (!position.isActive()) {
            positionsForHistory.add(position);
        }

        // Save if we've reached the threshold
        if (changes >= CACHE_FLUSH_THRESHOLD) {
            flushCache();
        }
    }

    /**
     * Remove a position
     * 
     * @param positionId the ID of the position to remove
     * @return the removed position, or null if not found
     */
    public WatchedPosition removePosition(String positionId) {
        WatchedPosition position = positions.remove(positionId);

        if (position != null) {
            // Mark for update
            modifiedPositionIds.add(positionId); // Add to trigger active positions save
            pendingChanges.incrementAndGet();

            // Queue for history if it was active
            if (position.isActive()) {
                position.updateStatus(PositionStatus.CLOSED, "Manually removed from repository");
                positionsForHistory.add(position);
            }

            // Flush immediately on removal to prevent race conditions
            flushCache();
        }

        return position;
    }

    /**
     * Get a position by ID
     * 
     * @param positionId the position ID
     * @return the position, or null if not found
     */
    public WatchedPosition getPosition(String positionId) {
        return positions.get(positionId);
    }

    /**
     * Get all positions
     * 
     * @return list of all positions
     */
    public List<WatchedPosition> getAllPositions() {
        return new ArrayList<>(positions.values());
    }

    /**
     * Get only active positions
     * 
     * @return list of active positions
     */
    public List<WatchedPosition> getActivePositions() {
        List<WatchedPosition> activePositions = new ArrayList<>();

        for (WatchedPosition position : positions.values()) {
            if (position.isActive()) {
                activePositions.add(position);
            }
        }

        return activePositions;
    }

    /**
     * Update the status of a position
     * 
     * @param positionId the position ID
     * @param status     the new status
     * @param reason     the reason for the status change
     * @return true if the position was found and updated
     */
    public boolean updatePositionStatus(String positionId, PositionStatus status, String reason) {
        WatchedPosition position = positions.get(positionId);

        if (position == null) {
            return false;
        }

        boolean wasActive = position.isActive();
        boolean updated = position.updateStatus(status, reason);

        if (updated) {
            // Mark as modified
            modifiedPositionIds.add(positionId);
            int changes = pendingChanges.incrementAndGet();

            // If position was active but is now inactive, queue for history
            if (wasActive && !position.isActive()) {
                positionsForHistory.add(position);
            }

            // Save if we've reached the threshold
            if (changes >= CACHE_FLUSH_THRESHOLD) {
                flushCache();
            }
        }

        return updated;
    }

    /**
     * Flush cache to disk
     */
    public synchronized void flushCache() {
        if (pendingChanges.get() > 0) {
            LOGGER.fine("Flushing position cache with " + pendingChanges.get() + " pending changes");

            // Save active positions file
            saveActivePositions();

            // Save any positions to history
            for (WatchedPosition position : positionsForHistory) {
                saveToHistory(position);
            }

            // Clear caches
            modifiedPositionIds.clear();
            positionsForHistory.clear();
            pendingChanges.set(0);

            LOGGER.fine("Cache flush complete");
        }
    }

    /**
     * Save all active positions to file
     */
    private void saveActivePositions() {
        try {
            List<WatchedPosition> activePositions = getActivePositions();

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(ACTIVE_POSITIONS_FILE))) {
                oos.writeObject(activePositions);
            }

            LOGGER.fine("Saved " + activePositions.size() + " active positions to " + ACTIVE_POSITIONS_FILE);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving active positions", e);
        }
    }

    /**
     * Save a position to the history directory
     * 
     * @param position the position to save
     */
    private void saveToHistory(WatchedPosition position) {
        try {
            // Create a filename with position ID and timestamp
            String filename = String.format("%s_%s.dat",
                    position.getId(),
                    LocalDateTime.now().toString().replace(":", "-").replace(".", "-"));

            File historyFile = new File(HISTORY_DIR, filename);

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(historyFile))) {
                oos.writeObject(position);
            }

            LOGGER.fine("Saved position to history: " + historyFile.getPath());
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving position to history: " + position.getId(), e);
        }
    }

    /**
     * Load saved positions from file
     */
    @SuppressWarnings("unchecked")
    private void loadPositions() {
        File activeFile = new File(ACTIVE_POSITIONS_FILE);

        if (!activeFile.exists()) {
            LOGGER.info("No saved positions file found");
            return;
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(activeFile))) {
            List<WatchedPosition> loadedPositions = (List<WatchedPosition>) ois.readObject();

            for (WatchedPosition position : loadedPositions) {
                positions.put(position.getId(), position);
            }

            LOGGER.info("Loaded " + loadedPositions.size() + " positions from " + ACTIVE_POSITIONS_FILE);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error loading positions", e);
        }
    }

    /**
     * Clear all positions (for testing)
     */
    public void clearAllPositions() {
        positions.clear();
        modifiedPositionIds.clear();
        positionsForHistory.clear();
        pendingChanges.set(0);
        saveActivePositions();
        LOGGER.info("Cleared all positions");
    }

    /**
     * Shutdown hook for clean resource management
     */
    public void shutdown() {
        try {
            LOGGER.info("Shutting down PositionRepository");

            // Flush any pending changes
            flushCache();

            // Shut down scheduler
            cacheFlushScheduler.shutdown();
            if (!cacheFlushScheduler.awaitTermination(5, TimeUnit.SECONDS)) {
                cacheFlushScheduler.shutdownNow();
            }

            LOGGER.info("PositionRepository shutdown complete");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during PositionRepository shutdown", e);
        }
    }
}