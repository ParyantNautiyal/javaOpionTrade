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
import java.util.HashMap;
import java.io.IOException;

/**
 * Repository for persisting positions
 */
@Singleton
public class PositionRepository {
    private static final Logger LOGGER = Logger.getLogger(PositionRepository.class.getName());

    private static final String DATA_DIR = "data/positions";
    private static final String ACTIVE_POSITIONS_FILE = DATA_DIR + "/active_positions.dat";
    private static final String HISTORY_DIR = DATA_DIR + "/history";
    private static final String CLOSED_POSITIONS_JSONL = DATA_DIR + "/closed_positions.jsonl";

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
        LOGGER.warning("JSONL-DEBUG: Removing position: " + positionId);

        WatchedPosition position = positions.remove(positionId);

        if (position != null) {
            // Mark for update
            modifiedPositionIds.add(positionId); // Add to trigger active positions save
            pendingChanges.incrementAndGet();

            // Queue for history if it was active
            if (position.isActive()) {
                LOGGER.warning("JSONL-DEBUG: Position was active, updating status to CLOSED: " + positionId);
                position.updateStatus(PositionStatus.CLOSED, "Manually removed from repository");
                positionsForHistory.add(position);
            } else {
                LOGGER.warning("JSONL-DEBUG: Position was already inactive: " + positionId);
                positionsForHistory.add(position);
            }

            // Flush immediately on removal to prevent race conditions
            LOGGER.warning("JSONL-DEBUG: Flushing cache after position removal: " + positionId);
            flushCache();
        } else {
            LOGGER.warning("JSONL-DEBUG: Position not found for removal: " + positionId);
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
        LOGGER.warning("JSONL-DEBUG: Flush cache called with " + pendingChanges.get() + " changes and " +
                positionsForHistory.size() + " positions for history");

        if (pendingChanges.get() > 0) {
            LOGGER.warning("JSONL-DEBUG: Starting to flush position cache");

            // Save active positions file
            saveActivePositions();

            // Save any positions to history
            int historyCount = 0;
            for (WatchedPosition position : positionsForHistory) {
                LOGGER.warning("JSONL-DEBUG: Saving position to history: " + position.getId() +
                        " (Status: " + position.getStatus() + ")");
                saveToHistory(position);
                historyCount++;
            }
            LOGGER.warning("JSONL-DEBUG: Saved " + historyCount + " positions to history");

            // Clear caches
            modifiedPositionIds.clear();
            positionsForHistory.clear();
            pendingChanges.set(0);

            LOGGER.warning("JSONL-DEBUG: Cache flush complete");
        } else {
            LOGGER.warning("JSONL-DEBUG: No changes to flush");
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
     * Export a closed position to JSONL file
     * 
     * @param position the closed position to export
     */
    private void exportClosedPositionToJsonl(WatchedPosition position) {
        LOGGER.warning("JSONL-DEBUG: Starting export of position to JSONL: " + position.getId());

        if (position == null) {
            LOGGER.warning("JSONL-DEBUG: Position is null, cannot export");
            return;
        }

        if (position.isActive()) {
            LOGGER.warning("JSONL-DEBUG: Position " + position.getId() + " is still active, skipping JSONL export");
            return;
        }

        try {
            // Ensure data directory exists
            File dataDir = new File(DATA_DIR);
            if (!dataDir.exists()) {
                LOGGER.warning("JSONL-DEBUG: Creating data directory: " + DATA_DIR);
                boolean created = dataDir.mkdirs();
                if (!created) {
                    LOGGER.severe("JSONL-DEBUG: Failed to create data directory: " + DATA_DIR);
                    return;
                }
            }

            LOGGER.warning("JSONL-DEBUG: Creating position data for JSON: " + position.getId());

            // Create JSON object with position data
            Map<String, Object> positionData = new HashMap<>();
            positionData.put("id", position.getId());

            try {
                if (position.getInstrument() != null) {
                    positionData.put("symbol", position.getInstrument().getTradingSymbol());
                } else {
                    LOGGER.warning("JSONL-DEBUG: Position " + position.getId() + " has null instrument");
                    positionData.put("symbol", "UNKNOWN");
                }
            } catch (Exception e) {
                LOGGER.warning("JSONL-DEBUG: Error getting symbol: " + e.getMessage());
                positionData.put("symbol", "ERROR");
            }

            positionData.put("orderType", position.getOrderType().toString());
            positionData.put("quantity", position.getQuantity());
            positionData.put("entryPrice", position.getEntryPrice());
            positionData.put("entryTime", position.getEntryTime().toString());
            positionData.put("exitPrice", position.getCurrentPrice());
            positionData.put("exitTime", position.getStatusChangeTime().toString());
            positionData.put("pnl", position.getPnl());
            positionData.put("pnlPercent", position.getPnlPercent());
            positionData.put("stopLoss", position.getStopLoss());
            positionData.put("status", position.getStatus().toString());
            positionData.put("reason", position.getStatusReason());

            LOGGER.warning("JSONL-DEBUG: Converting to JSON string");

            // Convert to JSON string - check if org.json library is available
            String jsonLine;
            try {
                jsonLine = new org.json.JSONObject(positionData).toString() + "\n";
                LOGGER.warning("JSONL-DEBUG: JSON conversion successful");
            } catch (NoClassDefFoundError e) {
                LOGGER.severe("JSONL-DEBUG: org.json library is missing! " + e.getMessage());
                // Fallback to simple string representation
                jsonLine = positionData.toString() + "\n";
            }

            // Create file path and ensure it's valid
            File jsonlFile = new File(CLOSED_POSITIONS_JSONL);
            LOGGER.warning("JSONL-DEBUG: Attempting to write to file: " + jsonlFile.getAbsolutePath());
            boolean fileExists = jsonlFile.exists();

            // Append to JSONL file
            try (java.io.FileWriter writer = new java.io.FileWriter(jsonlFile, true)) {
                writer.write(jsonLine);
                LOGGER.warning("JSONL-DEBUG: Successfully wrote to JSONL file");
            } catch (java.io.IOException e) {
                LOGGER.severe("JSONL-DEBUG: I/O error writing to JSONL file: " + e.getMessage());
                // Try to get more details about the file location
                LOGGER.severe("JSONL-DEBUG: File absolute path: " + jsonlFile.getAbsolutePath());
                LOGGER.severe("JSONL-DEBUG: Parent directory exists: "
                        + (jsonlFile.getParentFile() != null && jsonlFile.getParentFile().exists()));
                LOGGER.severe("JSONL-DEBUG: Can write to directory: "
                        + (jsonlFile.getParentFile() != null && jsonlFile.getParentFile().canWrite()));
            }

            if (!fileExists) {
                LOGGER.warning("JSONL-DEBUG: Created new closed positions JSONL file: " + CLOSED_POSITIONS_JSONL);
            }

            LOGGER.warning("JSONL-DEBUG: Completed export of position to JSONL: " + position.getId());
        } catch (Exception e) {
            LOGGER.severe("JSONL-DEBUG: Unexpected error exporting position to JSONL: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Save a position to the history directory
     * 
     * @param position the position to save
     */
    private void saveToHistory(WatchedPosition position) {
        LOGGER.warning("JSONL-DEBUG: saveToHistory called for position: " + position.getId());

        try {
            // Export to JSONL first
            LOGGER.warning("JSONL-DEBUG: About to call exportClosedPositionToJsonl for: " + position.getId());
            exportClosedPositionToJsonl(position);
            LOGGER.warning("JSONL-DEBUG: exportClosedPositionToJsonl completed for: " + position.getId());

            // Ensure history directory exists
            File historyDir = new File(HISTORY_DIR);
            if (!historyDir.exists()) {
                LOGGER.warning("JSONL-DEBUG: Creating history directory: " + HISTORY_DIR);
                historyDir.mkdirs();
            }

            // Create a filename with position ID and timestamp
            String filename = String.format("%s_%s.dat",
                    position.getId(),
                    LocalDateTime.now().toString().replace(":", "-").replace(".", "-"));

            File historyFile = new File(HISTORY_DIR, filename);
            LOGGER.warning("JSONL-DEBUG: Writing position to history file: " + historyFile.getPath());

            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(historyFile))) {
                oos.writeObject(position);
                LOGGER.warning("JSONL-DEBUG: Successfully wrote position to history file");
            } catch (IOException e) {
                LOGGER.severe("JSONL-DEBUG: I/O error writing to history file: " + e.getMessage());
            }

            LOGGER.warning("JSONL-DEBUG: Completed saving position to history: " + historyFile.getPath());
        } catch (Exception e) {
            LOGGER.severe("JSONL-DEBUG: Error saving position to history: " + position.getId() + ", Error: "
                    + e.getMessage());
            e.printStackTrace();
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

            // Export any remaining inactive positions to JSONL
            for (WatchedPosition position : positions.values()) {
                if (!position.isActive()) {
                    exportClosedPositionToJsonl(position);
                }
            }

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

    /**
     * Get all closed positions from JSONL file
     * 
     * @return list of closed positions as Map objects
     */
    public List<Map<String, Object>> getClosedPositionsFromJsonl() {
        List<Map<String, Object>> closedPositions = new ArrayList<>();

        File jsonlFile = new File(CLOSED_POSITIONS_JSONL);
        if (!jsonlFile.exists()) {
            LOGGER.info("No closed positions JSONL file found");
            return closedPositions;
        }

        try (java.io.BufferedReader reader = new java.io.BufferedReader(new java.io.FileReader(jsonlFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.trim().isEmpty()) {
                    continue;
                }

                try {
                    // Parse JSON to Map
                    org.json.JSONObject json = new org.json.JSONObject(line);
                    Map<String, Object> position = new HashMap<>();

                    for (String key : json.keySet()) {
                        position.put(key, json.get(key));
                    }

                    closedPositions.add(position);
                } catch (Exception e) {
                    LOGGER.warning("Error parsing JSON line: " + e.getMessage());
                }
            }

            LOGGER.info("Loaded " + closedPositions.size() + " closed positions from JSONL");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error reading closed positions JSONL: " + e.getMessage(), e);
        }

        return closedPositions;
    }
}