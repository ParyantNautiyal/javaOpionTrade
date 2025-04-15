package com.optiontrading.service.order;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import com.optiontrading.service.model.OrderHistoryEntry;
import com.optiontrading.config.ConfigurationManager;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Repository for storing and retrieving order history
 */
@Singleton
public class OrderHistoryRepository {
    private static final Logger LOGGER = Logger.getLogger(OrderHistoryRepository.class.getName());
    private static final String DEFAULT_HISTORY_DIR = "data/order_history";

    private final Map<String, OrderHistoryEntry> orderHistoryCache = new ConcurrentHashMap<>();
    private final ObjectMapper objectMapper;
    private final String historyDir;
    private final Path historyFilePath;

    @Inject
    public OrderHistoryRepository(ConfigurationManager configManager) {
        this.objectMapper = new ObjectMapper();
        objectMapper.registerModule(new JavaTimeModule());

        // Get the directory from config or use default
        this.historyDir = configManager.getString("order.history.dir", DEFAULT_HISTORY_DIR);
        this.historyFilePath = Paths.get(historyDir, "order_history.jsonl");

        // Create directories if they don't exist
        createDirectories();

        // Load existing history into cache
        loadHistoryIntoCache();

        LOGGER.info("Initialized OrderHistoryRepository. History directory: " + historyDir);
    }

    private void createDirectories() {
        try {
            Files.createDirectories(Paths.get(historyDir));
            LOGGER.info("Created order history directory: " + historyDir);
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to create order history directory", e);
        }
    }

    /**
     * Load history from file into cache on startup
     */
    private void loadHistoryIntoCache() {
        if (!Files.exists(historyFilePath)) {
            LOGGER.info("No order history file found at: " + historyFilePath);
            return;
        }

        try (BufferedReader reader = Files.newBufferedReader(historyFilePath)) {
            String line;
            int count = 0;
            while ((line = reader.readLine()) != null) {
                try {
                    OrderHistoryEntry entry = objectMapper.readValue(line, OrderHistoryEntry.class);
                    orderHistoryCache.put(entry.getOrderId(), entry);
                    count++;
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Failed to parse order history entry: " + line, e);
                }
            }
            LOGGER.info("Loaded " + count + " order history entries into cache");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to load order history from file", e);
        }
    }

    /**
     * Save or update an order history entry
     */
    public void saveOrderHistory(OrderHistoryEntry entry) {
        if (entry == null || entry.getOrderId() == null) {
            LOGGER.warning("Cannot save null entry or entry with null orderId");
            return;
        }

        // Update cache
        orderHistoryCache.put(entry.getOrderId(), entry);

        // Append to file
        try (BufferedWriter writer = Files.newBufferedWriter(
                historyFilePath,
                StandardOpenOption.CREATE,
                StandardOpenOption.APPEND)) {
            writer.write(objectMapper.writeValueAsString(entry));
            writer.newLine();
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to save order history: " + entry.getOrderId(), e);
        }
    }

    /**
     * Get an order history entry by ID
     */
    public OrderHistoryEntry getOrderHistory(String orderId) {
        return orderHistoryCache.get(orderId);
    }

    /**
     * Get all order history entries
     */
    public List<OrderHistoryEntry> getAllOrderHistory() {
        return new ArrayList<>(orderHistoryCache.values());
    }

    /**
     * Get the most recent order history entries
     * 
     * @param limit maximum number of entries to return
     * @return list of recent order history entries
     */
    public List<OrderHistoryEntry> getRecentOrderHistory(int limit) {
        List<OrderHistoryEntry> all = new ArrayList<>(orderHistoryCache.values());
        all.sort((a, b) -> b.getCreatedAt().compareTo(a.getCreatedAt())); // Sort descending

        return all.stream()
                .limit(limit)
                .collect(Collectors.toList());
    }

    /**
     * Read order history directly from the file (for very large histories)
     */
    public List<OrderHistoryEntry> readOrderHistoryFromFile(int limit) {
        if (!Files.exists(historyFilePath)) {
            return Collections.emptyList();
        }

        try (Stream<String> lines = Files.lines(historyFilePath)) {
            return lines.skip(Math.max(0, countLines() - limit))
                    .map(line -> {
                        try {
                            return objectMapper.readValue(line, OrderHistoryEntry.class);
                        } catch (Exception e) {
                            LOGGER.warning("Failed to parse line: " + line);
                            return null;
                        }
                    })
                    .filter(entry -> entry != null)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Failed to read order history from file", e);
            return Collections.emptyList();
        }
    }

    private long countLines() {
        try {
            return Files.lines(historyFilePath).count();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Failed to count lines in history file", e);
            return 0;
        }
    }

    /**
     * Clear the cache (useful for testing)
     */
    public void clearCache() {
        orderHistoryCache.clear();
    }
}