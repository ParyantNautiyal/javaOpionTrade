package com.optiontrading.service.market;

import com.optiontrading.events.Event;
import com.optiontrading.events.EventBus;
import com.optiontrading.resources.ResourceManager;
import com.optiontrading.resources.TimerManager;
import com.optiontrading.service.api.TradingApiClient;

import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Provider for real-time market data
 */
public class MarketDataProvider implements MarketDataService {
    private static final Logger LOGGER = Logger.getLogger(MarketDataProvider.class.getName());
    private static MarketDataProvider INSTANCE;

    // Default update interval - 5 seconds
    private static final int DEFAULT_UPDATE_INTERVAL = 5000;

    // Batch size for API calls
    private static final int BATCH_SIZE = 50;

    // Map of instrument ID to subscribers
    private final Map<String, Set<MarketDataSubscriber>> subscribers = new ConcurrentHashMap<>();

    // Set of all instruments being monitored
    private final Set<String> monitoredInstruments = ConcurrentHashMap.newKeySet();

    // Map of instrument ID to last price
    private final Map<String, BigDecimal> lastPrices = new ConcurrentHashMap<>();

    // Timer for regular price updates
    private Timer updateTimer;

    // Resource manager for timers
    private final TimerManager timerManager;

    // Event bus
    private final EventBus eventBus;

    // Trading API client
    private final TradingApiClient tradingApiClient;

    /**
     * Private constructor for singleton instance
     */
    private MarketDataProvider() {
        ResourceManager resourceManager = ResourceManager.getInstance();
        this.timerManager = resourceManager.getTimerManager();

        // For backward compatibility, use the static instances
        this.tradingApiClient = com.optiontrading.service.api.KiteConnectClient.getInstance();
        this.eventBus = com.optiontrading.events.EventBus.getInstance();

        LOGGER.info("Initialized MarketDataProvider singleton instance");
    }

    /**
     * Constructor with dependency injection
     * 
     * @param tradingApiClient the trading API client
     * @param eventBus         the event bus
     */
    public MarketDataProvider(TradingApiClient tradingApiClient, EventBus eventBus) {
        this.tradingApiClient = tradingApiClient;
        this.eventBus = eventBus;
        this.timerManager = ResourceManager.getInstance().getTimerManager();
        LOGGER.info("Initialized MarketDataProvider with dependency injection");
    }

    /**
     * Get the singleton instance (for backward compatibility)
     */
    public static synchronized MarketDataProvider getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new MarketDataProvider();
        }
        return INSTANCE;
    }

    /**
     * Start the market data provider
     */
    @Override
    public void start() {
        LOGGER.info("Starting MarketDataProvider");

        // Start update timer if not already running
        if (updateTimer == null) {
            updateTimer = timerManager.createTimer("MarketData-Updater", true);
            updateTimer.scheduleAtFixedRate(new PriceUpdateTask(), 0, DEFAULT_UPDATE_INTERVAL);
            LOGGER.info("Started price update timer with interval: " + DEFAULT_UPDATE_INTERVAL + "ms");
        }
    }

    /**
     * Subscribe to market data for instruments
     * 
     * @param instruments the list of instruments to subscribe to
     * @param subscriber  the subscriber to receive updates
     */
    @Override
    public void subscribe(List<String> instruments, MarketDataSubscriber subscriber) {
        if (instruments == null || instruments.isEmpty() || subscriber == null) {
            return;
        }

        LOGGER.info("Subscribing to " + instruments.size() + " instruments");

        // Start the provider if it's not already running
        if (updateTimer == null) {
            start();
        }

        // Subscribe to each instrument
        for (String instrumentId : instruments) {
            // Add to subscribers map
            subscribers.computeIfAbsent(instrumentId, k -> ConcurrentHashMap.newKeySet())
                    .add(subscriber);

            // Add to monitored instruments
            monitoredInstruments.add(instrumentId);
        }

        // Get initial prices
        refreshPrices(instruments);
    }

    /**
     * Unsubscribe from market data for instruments
     * 
     * @param instruments the list of instruments to unsubscribe from
     * @param subscriber  the subscriber to remove
     */
    @Override
    public void unsubscribe(List<String> instruments, MarketDataSubscriber subscriber) {
        if (instruments == null || instruments.isEmpty() || subscriber == null) {
            return;
        }

        LOGGER.info("Unsubscribing from " + instruments.size() + " instruments");

        // Unsubscribe from each instrument
        for (String instrumentId : instruments) {
            Set<MarketDataSubscriber> subs = subscribers.get(instrumentId);
            if (subs != null) {
                subs.remove(subscriber);

                // If no more subscribers, remove from monitored instruments
                if (subs.isEmpty()) {
                    subscribers.remove(instrumentId);
                    monitoredInstruments.remove(instrumentId);
                }
            }
        }
    }

    /**
     * Get the last known price for an instrument
     * 
     * @param instrumentId the instrument ID
     * @return the last price or null if not available
     */
    @Override
    public BigDecimal getLastPrice(String instrumentId) {
        return lastPrices.get(instrumentId);
    }

    /**
     * Get the last known prices for multiple instruments
     * 
     * @param instrumentIds the list of instrument IDs
     * @return map of instrument ID to last price
     */
    @Override
    public Map<String, BigDecimal> getLastPrices(List<String> instrumentIds) {
        if (instrumentIds == null || instrumentIds.isEmpty()) {
            return new HashMap<>();
        }

        Map<String, BigDecimal> result = new HashMap<>();
        for (String instrumentId : instrumentIds) {
            BigDecimal price = lastPrices.get(instrumentId);
            if (price != null) {
                result.put(instrumentId, price);
            }
        }

        return result;
    }

    /**
     * Force a refresh of market data
     * 
     * @param instrumentIds the list of instrument IDs to refresh
     * @return map of instrument ID to updated price
     */
    @Override
    public Map<String, BigDecimal> refreshPrices(List<String> instrumentIds) {
        if (instrumentIds == null || instrumentIds.isEmpty()) {
            return new HashMap<>();
        }

        try {
            // Get prices from the trading API
            Map<String, BigDecimal> prices = tradingApiClient.getLTP(instrumentIds);

            // Update last prices
            lastPrices.putAll(prices);

            // Notify subscribers
            for (Map.Entry<String, BigDecimal> entry : prices.entrySet()) {
                String instrumentId = entry.getKey();
                BigDecimal price = entry.getValue();

                Set<MarketDataSubscriber> subs = subscribers.get(instrumentId);
                if (subs != null) {
                    for (MarketDataSubscriber subscriber : subs) {
                        try {
                            subscriber.onPriceUpdate(instrumentId, price);
                        } catch (Exception e) {
                            LOGGER.log(Level.WARNING, "Error notifying subscriber: " + e.getMessage(), e);
                        }
                    }
                }
            }

            return prices;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error refreshing prices: " + e.getMessage(), e);
            return new HashMap<>();
        }
    }

    /**
     * Shutdown the market data provider
     */
    @Override
    public void shutdown() {
        LOGGER.info("Shutting down MarketDataProvider");
        if (updateTimer != null) {
            timerManager.cancelTimer("MarketData-Updater");
            updateTimer = null;
        }

        // Clear all collections
        subscribers.clear();
        monitoredInstruments.clear();
        lastPrices.clear();
    }

    /**
     * Split a list into batches of a maximum size
     */
    private <T> List<List<T>> createBatches(Collection<T> collection, int batchSize) {
        List<List<T>> batches = new ArrayList<>();
        List<T> batch = new ArrayList<>(batchSize);

        for (T item : collection) {
            batch.add(item);
            if (batch.size() >= batchSize) {
                batches.add(batch);
                batch = new ArrayList<>(batchSize);
            }
        }

        if (!batch.isEmpty()) {
            batches.add(batch);
        }

        return batches;
    }

    /**
     * Task for updating prices periodically
     */
    private class PriceUpdateTask extends TimerTask {
        @Override
        public void run() {
            try {
                // Get the current set of monitored instruments
                Set<String> instruments = new HashSet<>(monitoredInstruments);

                if (instruments.isEmpty()) {
                    return;
                }

                // Create batches of instruments (Kite API has limits on number of instruments
                // per request)
                List<List<String>> batches = createBatches(instruments, BATCH_SIZE);

                // Fetch prices for each batch
                Map<String, BigDecimal> allPrices = new HashMap<>();

                for (List<String> batch : batches) {
                    try {
                        Map<String, BigDecimal> batchPrices = tradingApiClient.getLTP(batch);
                        allPrices.putAll(batchPrices);
                    } catch (RuntimeException e) {
                        if (e.getMessage() != null && e.getMessage().contains("Not authenticated")) {
                            // Authentication error - trigger re-authentication event
                            LOGGER.warning("Authentication error during price update: " + e.getMessage());

                            // Publish event that can be handled by the main application
                            eventBus.publishAsync(new AuthenticationRequiredEvent());

                            // Skip further processing for this batch
                            continue;
                        } else {
                            // Other API error - log and continue with next batch
                            LOGGER.log(Level.WARNING, "Error fetching prices for a batch: " + e.getMessage(), e);
                        }
                    }
                }

                // Update the last prices map
                lastPrices.putAll(allPrices);

                // Notify subscribers of price updates
                for (Map.Entry<String, BigDecimal> entry : allPrices.entrySet()) {
                    String instrumentId = entry.getKey();
                    BigDecimal price = entry.getValue();

                    Set<MarketDataSubscriber> subs = subscribers.get(instrumentId);
                    if (subs != null) {
                        for (MarketDataSubscriber subscriber : subs) {
                            try {
                                subscriber.onPriceUpdate(instrumentId, price);
                            } catch (Exception e) {
                                LOGGER.log(Level.WARNING, "Error notifying subscriber", e);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error in price update task", e);
            }
        }
    }

    /**
     * Event indicating authentication is required
     */
    public static class AuthenticationRequiredEvent extends Event {
        public AuthenticationRequiredEvent() {
            // Default constructor
        }
    }
}