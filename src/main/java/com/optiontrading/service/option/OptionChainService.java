package com.optiontrading.service.option;

import com.optiontrading.events.EventBus;
import com.optiontrading.resources.ThreadManager;
import com.optiontrading.service.instrument.InstrumentService;
import com.optiontrading.service.market.MarketDataService;
import com.optiontrading.service.market.MarketDataSubscriber;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OptionPair;
import com.optiontrading.service.model.OptionType;
import com.google.inject.Inject;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Service for monitoring option chains and finding best options based on
 * criteria
 */
public class OptionChainService implements MarketDataSubscriber {
    private static final Logger LOGGER = Logger.getLogger(OptionChainService.class.getName());

    private String orderId;
    private BigDecimal targetPremium;

    // Set of all instruments being monitored
    private final Set<String> monitoredInstrumentIds = ConcurrentHashMap.newKeySet();

    // Map of instrument ID to instrument
    private final Map<String, Instrument> monitoredInstruments = new ConcurrentHashMap<>();

    // Map of instrument ID to current price
    private final Map<String, BigDecimal> currentPrices = new ConcurrentHashMap<>();

    // Current best option pair
    private volatile OptionPair currentBestPair;

    // Services and resources
    private final MarketDataService marketDataService;
    private final InstrumentService instrumentService;
    private final ThreadManager threadManager;
    private final EventBus eventBus;

    // Thread pool for analysis operations
    private ExecutorService analysisExecutor;

    // Add timestamp tracking for debouncing
    private volatile long lastUpdateTime = 0;
    private static final long UPDATE_INTERVAL_MS = 2000; // 2 seconds

    // Add price change tracking for early return
    private Map<String, BigDecimal> lastProcessedPrices = new ConcurrentHashMap<>();
    private static final BigDecimal SIGNIFICANT_PRICE_CHANGE_THRESHOLD = new BigDecimal("0.05"); // 0.05%

    /**
     * Create a new option chain service instance with dependency injection
     */
    @Inject
    public OptionChainService(
            MarketDataService marketDataService,
            InstrumentService instrumentService,
            ThreadManager threadManager,
            EventBus eventBus) {

        this.marketDataService = marketDataService;
        this.instrumentService = instrumentService;
        this.threadManager = threadManager;
        this.eventBus = eventBus;

        LOGGER.info("Created OptionChainService with dependency injection");
    }

    /**
     * Initialize this service for a specific order
     * 
     * @param orderId       the order ID
     * @param targetPremium the target premium
     */
    public void initialize(String orderId, BigDecimal targetPremium) {
        this.orderId = orderId;
        this.targetPremium = targetPremium;

        // Create a dedicated thread pool for this service
        this.analysisExecutor = threadManager.createSingleThreadExecutor("OptionAnalysis-" + orderId);

        LOGGER.info("Initialized OptionChainService for order " + orderId +
                " with target premium " + targetPremium);
    }

    /**
     * Start monitoring specified instruments
     * 
     * @param instruments list of instruments to monitor
     */
    public void monitorInstruments(List<Instrument> instruments) {
        if (instruments == null || instruments.isEmpty()) {
            LOGGER.warning("No instruments to monitor");
            return;
        }

        LOGGER.info("Starting to monitor " + instruments.size() + " instruments for order " + orderId);

        // Store instruments for reference
        for (Instrument instrument : instruments) {
            monitoredInstruments.put(instrument.getInstrumentId(), instrument);
            monitoredInstrumentIds.add(instrument.getInstrumentId());
        }

        // Subscribe to market data
        marketDataService.subscribe(new ArrayList<>(monitoredInstrumentIds), this);
    }

    /**
     * Implement MarketDataSubscriber
     */
    @Override
    public void onPriceUpdate(String instrumentId, BigDecimal price) {
        currentPrices.put(instrumentId, price);

        // Don't analyze on every update to avoid excessive CPU usage
        // Use a debounce strategy - only update periodically
        if (shouldUpdateBestPair()) {
            updateBestOptionPair();
        }
    }

    /**
     * Determine if we should update the best pair calculation
     * This implements a time-based debouncing strategy
     */
    private boolean shouldUpdateBestPair() {
        long currentTime = System.currentTimeMillis();
        long timeSinceLastUpdate = currentTime - lastUpdateTime;

        // Only update if enough time has passed since last update
        if (timeSinceLastUpdate >= UPDATE_INTERVAL_MS) {
            lastUpdateTime = currentTime;
            return true;
        }

        return false;
    }

    /**
     * Update the best option pair based on current prices
     */
    private void updateBestOptionPair() {
        // Check if prices have changed significantly before proceeding
        if (!havePricesChangedSignificantly()) {
            return;
        }

        // Run in a background thread to avoid blocking
        analysisExecutor.submit(() -> {
            try {
                OptionPair newBestPair = findBestOptionPair();
                if (isSignificantChange(currentBestPair, newBestPair)) {
                    OptionPair oldBestPair = currentBestPair;
                    currentBestPair = newBestPair;

                    // Log the change
                    LOGGER.info("Updated best option pair for order " + orderId +
                            " from " + oldBestPair + " to " + newBestPair);

                    // Publish event immediately in a synchronous manner to ensure delivery
                    try {
                        eventBus.publish(new BestOptionsUpdatedEvent(orderId,
                                Collections.singletonList(newBestPair)));
                        LOGGER.info("Successfully published BestOptionsUpdatedEvent synchronously");
                    } catch (Exception e) {
                        LOGGER.severe("Error publishing BestOptionsUpdatedEvent: " + e.getMessage());
                        e.printStackTrace();
                    }
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error updating best option pair", e);
            }
        });
    }

    /**
     * Check if any prices have changed significantly since the last update
     * 
     * @return true if prices have changed significantly
     */
    private boolean havePricesChangedSignificantly() {
        boolean hasChanges = false;

        // If no previous prices, consider it changed
        if (lastProcessedPrices.isEmpty()) {
            // Save current prices as the last processed
            lastProcessedPrices.putAll(currentPrices);
            return true;
        }

        // Check for new instruments or significant price changes
        for (Map.Entry<String, BigDecimal> entry : currentPrices.entrySet()) {
            String instrumentId = entry.getKey();
            BigDecimal currentPrice = entry.getValue();

            // If this is a new instrument or price has changed significantly
            if (!lastProcessedPrices.containsKey(instrumentId) ||
                    hasSignificantPriceChange(lastProcessedPrices.get(instrumentId), currentPrice)) {
                hasChanges = true;
                break;
            }
        }

        // If changes were detected, update the last processed prices
        if (hasChanges) {
            lastProcessedPrices.clear();
            lastProcessedPrices.putAll(currentPrices);
        }

        return hasChanges;
    }

    /**
     * Check if the price change is significant
     * 
     * @param oldPrice the old price
     * @param newPrice the new price
     * @return true if the change is significant
     */
    private boolean hasSignificantPriceChange(BigDecimal oldPrice, BigDecimal newPrice) {
        if (oldPrice == null || newPrice == null) {
            return true;
        }

        // Calculate percentage change
        BigDecimal change = percentageDifference(oldPrice, newPrice).abs();
        return change.compareTo(SIGNIFICANT_PRICE_CHANGE_THRESHOLD) > 0;
    }

    /**
     * Find the best option pair based on the target premium
     * 
     * @return the best option pair
     */
    private OptionPair findBestOptionPair() {
        // If we don't have enough data, return null or the current best pair
        if (currentPrices.isEmpty() || monitoredInstruments.isEmpty()) {
            LOGGER.warning("Cannot find best pair: insufficient data - prices: " +
                    currentPrices.size() + ", instruments: " + monitoredInstruments.size());
            return currentBestPair;
        }

        LOGGER.info("Finding best option pair for order " + orderId +
                " with target premium " + targetPremium +
                " - monitoring " + monitoredInstruments.size() + " instruments with " +
                currentPrices.size() + " price points");

        // Filter call options
        List<Instrument> callOptions = monitoredInstruments.values().stream()
                .filter(Instrument::isCall)
                .collect(Collectors.toList());

        // Filter put options
        List<Instrument> putOptions = monitoredInstruments.values().stream()
                .filter(Instrument::isPut)
                .collect(Collectors.toList());

        LOGGER.info("Available options: " + callOptions.size() + " calls, " + putOptions.size() + " puts");

        // Find best call option (closest to target premium)
        Instrument bestCallOption = null;
        BigDecimal bestCallPrice = null;
        BigDecimal minCallDifference = BigDecimal.valueOf(Double.MAX_VALUE);

        for (Instrument callOption : callOptions) {
            String callId = callOption.getInstrumentId();
            BigDecimal callPrice = currentPrices.get(callId);

            if (callPrice == null) {
                LOGGER.fine("No price available for call " + callOption.getTradingSymbol());
                continue;
            }

            BigDecimal difference = callPrice.subtract(targetPremium).abs();
            if (difference.compareTo(minCallDifference) < 0) {
                minCallDifference = difference;
                bestCallOption = callOption;
                bestCallPrice = callPrice;
            }
        }

        // Find best put option (closest to target premium)
        Instrument bestPutOption = null;
        BigDecimal bestPutPrice = null;
        BigDecimal minPutDifference = BigDecimal.valueOf(Double.MAX_VALUE);

        for (Instrument putOption : putOptions) {
            String putId = putOption.getInstrumentId();
            BigDecimal putPrice = currentPrices.get(putId);

            if (putPrice == null) {
                LOGGER.fine("No price available for put " + putOption.getTradingSymbol());
                continue;
            }

            BigDecimal difference = putPrice.subtract(targetPremium).abs();
            if (difference.compareTo(minPutDifference) < 0) {
                minPutDifference = difference;
                bestPutOption = putOption;
                bestPutPrice = putPrice;
            }
        }

        // Create option pair with the best individual options
        OptionPair bestPair = null;
        if (bestCallOption != null && bestPutOption != null) {
            bestPair = new OptionPair(bestCallOption, bestCallPrice, bestPutOption, bestPutPrice);

            LOGGER.info("Best call option: " + bestCallOption.getTradingSymbol() + "@" + bestCallPrice +
                    " (diff: " + minCallDifference + ")");
            LOGGER.info("Best put option: " + bestPutOption.getTradingSymbol() + "@" + bestPutPrice +
                    " (diff: " + minPutDifference + ")");

            LOGGER.info("Best option pair found: " +
                    bestPair.getCallOption().getTradingSymbol() + "@" + bestPair.getCallPrice() +
                    " / " + bestPair.getPutOption().getTradingSymbol() + "@" + bestPair.getPutPrice());
        } else {
            LOGGER.warning("Could not find any suitable option pair for order " + orderId);
        }

        LOGGER.info("Best option search completed - best call difference: " +
                (bestCallOption != null ? minCallDifference : "N/A") +
                ", best put difference: " +
                (bestPutOption != null ? minPutDifference : "N/A"));

        return bestPair;
    }

    /**
     * Determine if there is a significant change between old and new best pairs
     * 
     * @param oldPair the old option pair
     * @param newPair the new option pair
     * @return true if the change is significant
     */
    private boolean isSignificantChange(OptionPair oldPair, OptionPair newPair) {
        // If either is null, consider it a significant change
        if (oldPair == null || newPair == null) {
            return true;
        }

        // If the instruments have changed, it's significant
        if (!Objects.equals(oldPair.getCallOption(), newPair.getCallOption()) ||
                !Objects.equals(oldPair.getPutOption(), newPair.getPutOption())) {
            return true;
        }

        // If price has changed by more than 1%, it's significant
        BigDecimal oldCallPrice = oldPair.getCallPrice();
        BigDecimal newCallPrice = newPair.getCallPrice();
        BigDecimal oldPutPrice = oldPair.getPutPrice();
        BigDecimal newPutPrice = newPair.getPutPrice();

        if (percentageDifference(oldCallPrice, newCallPrice).abs().compareTo(BigDecimal.valueOf(1)) > 0 ||
                percentageDifference(oldPutPrice, newPutPrice).abs().compareTo(BigDecimal.valueOf(1)) > 0) {
            return true;
        }

        return false;
    }

    /**
     * Calculate percentage difference between two values
     * 
     * @param oldValue the old value
     * @param newValue the new value
     * @return the percentage difference
     */
    private BigDecimal percentageDifference(BigDecimal oldValue, BigDecimal newValue) {
        if (oldValue.compareTo(BigDecimal.ZERO) == 0) {
            return BigDecimal.valueOf(100); // Arbitrary large value
        }

        return newValue.subtract(oldValue)
                .multiply(BigDecimal.valueOf(100))
                .divide(oldValue, 2, BigDecimal.ROUND_HALF_UP);
    }

    /**
     * Get the current best option pair
     * 
     * @return the current best option pair
     */
    public OptionPair getCurrentBestPair() {
        return currentBestPair;
    }

    /**
     * Stop monitoring and clean up resources
     */
    public void shutdown() {
        LOGGER.info("Shutting down OptionChainService for order " + orderId);

        // Unsubscribe from market data
        if (!monitoredInstrumentIds.isEmpty()) {
            marketDataService.unsubscribe(new ArrayList<>(monitoredInstrumentIds), this);
        }

        // Don't shut down the thread pool - ResourceManager will handle that

        // Clear collections
        monitoredInstrumentIds.clear();
        monitoredInstruments.clear();
        currentPrices.clear();
    }
}