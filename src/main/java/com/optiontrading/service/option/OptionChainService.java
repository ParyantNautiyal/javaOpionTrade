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
     * This implements a simple debounce strategy
     */
    private boolean shouldUpdateBestPair() {
        // This is a simple implementation that updates roughly once every 10 price
        // updates
        // In a real system, this would be more sophisticated
        return Math.random() < 0.1;
    }

    /**
     * Update the best option pair based on current prices
     */
    private void updateBestOptionPair() {
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

                    // Publish event with updated best pair
                    eventBus.publishAsync(new BestOptionsUpdatedEvent(orderId,
                            Collections.singletonList(newBestPair)));
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error updating best option pair", e);
            }
        });
    }

    /**
     * Find the best option pair based on the target premium
     * 
     * @return the best option pair
     */
    private OptionPair findBestOptionPair() {
        // If we don't have enough data, return null or the current best pair
        if (currentPrices.isEmpty() || monitoredInstruments.isEmpty()) {
            return currentBestPair;
        }

        // Filter call options
        List<Instrument> callOptions = monitoredInstruments.values().stream()
                .filter(Instrument::isCall)
                .collect(Collectors.toList());

        // Filter put options
        List<Instrument> putOptions = monitoredInstruments.values().stream()
                .filter(Instrument::isPut)
                .collect(Collectors.toList());

        // Find the best option pair
        OptionPair bestPair = null;
        BigDecimal minDifference = BigDecimal.valueOf(Double.MAX_VALUE);

        // For each call option
        for (Instrument callOption : callOptions) {
            String callId = callOption.getInstrumentId();
            BigDecimal callPrice = currentPrices.get(callId);

            if (callPrice == null)
                continue;

            // Find matching put option with same strike
            for (Instrument putOption : putOptions) {
                // Match strike prices
                if (putOption.getStrikePrice().compareTo(callOption.getStrikePrice()) != 0) {
                    continue;
                }

                String putId = putOption.getInstrumentId();
                BigDecimal putPrice = currentPrices.get(putId);

                if (putPrice == null)
                    continue;

                // Calculate how close these options are to our target premium
                OptionPair pair = new OptionPair(callOption, callPrice, putOption, putPrice);
                BigDecimal difference = pair.getPremiumDifference(targetPremium);

                // If this is better than our current best, update
                if (difference.compareTo(minDifference) < 0) {
                    minDifference = difference;
                    bestPair = pair;
                }
            }
        }

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