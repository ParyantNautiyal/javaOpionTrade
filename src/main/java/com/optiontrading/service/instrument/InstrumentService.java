package com.optiontrading.service.instrument;

import com.optiontrading.events.EventBus;
import com.optiontrading.resources.CacheManager;
import com.optiontrading.service.api.KiteConnectClient;
import com.optiontrading.service.api.TradingApiClient;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.InstrumentType;
import com.optiontrading.service.model.OptionType;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.io.File;
import java.io.FileOutputStream;
import java.io.ObjectOutputStream;
import java.io.FileInputStream;
import java.io.ObjectInputStream;
import java.io.IOException;
import java.util.Properties;
import java.util.Objects;
import java.time.format.DateTimeFormatter;
import java.time.Year;
import java.time.DayOfWeek;

/**
 * Service for downloading and managing instruments
 */
@Singleton
public class InstrumentService {
    private static final Logger LOGGER = Logger.getLogger(InstrumentService.class.getName());

    // Cache of all instruments
    private final Map<String, Instrument> instruments = new ConcurrentHashMap<>();

    // Map of index to current spot price
    private final Map<String, BigDecimal> indexSpotPrices = new ConcurrentHashMap<>();

    // Supported exchanges for F&O instruments
    private static final Set<String> SUPPORTED_FNO_EXCHANGES = Set.of("NFO", "BFO");

    // Resource managers and services
    private final CacheManager cacheManager;
    private final EventBus eventBus;
    private final TradingApiClient tradingApiClient;

    private static final String INSTRUMENTS_DIR = "data/instruments";

    /**
     * Constructor with dependency injection
     */
    @Inject
    public InstrumentService(CacheManager cacheManager, EventBus eventBus, TradingApiClient tradingApiClient) {
        this.cacheManager = cacheManager;
        this.eventBus = eventBus;
        this.tradingApiClient = tradingApiClient;

        // Don't load instruments automatically - wait until refreshInstruments is
        // called
        LOGGER.info("Created InstrumentService with dependency injection (initialization deferred)");
    }

    /**
     * Get an instrument by ID
     * 
     * @param instrumentId the instrument ID
     * @return the instrument or null if not found
     */
    public Instrument getInstrument(String instrumentId) {
        return instruments.get(instrumentId);
    }

    /**
     * Get all instruments
     * 
     * @return list of all instruments
     */
    public List<Instrument> getAllInstruments() {
        return new ArrayList<>(instruments.values());
    }

    /**
     * Get all option instruments
     * 
     * @return list of all option instruments
     */
    public List<Instrument> getAllOptionInstruments() {
        return instruments.values().stream()
                .filter(Instrument::isOption)
                .filter(i -> SUPPORTED_FNO_EXCHANGES.contains(i.getExchange()))
                .collect(Collectors.toList());
    }

    /**
     * Get the current spot price for an index
     * 
     * @param indexSymbol the index symbol
     * @return the spot price or null if not available
     */
    public BigDecimal getIndexSpotPrice(String indexSymbol) {
        return indexSpotPrices.get(indexSymbol.toUpperCase());
    }

    /**
     * Update the spot price for an index
     * 
     * @param indexSymbol the index symbol
     * @param price       the new price
     */
    public void updateIndexSpotPrice(String indexSymbol, BigDecimal price) {
        indexSpotPrices.put(indexSymbol.toUpperCase(), price);
    }

    /**
     * Filter instruments based on index, expiry, and strike range
     * Focuses specifically on options from NFO and BFO exchanges
     * 
     * @param indexSymbol the index symbol (e.g., "NIFTY")
     * @param expiryDate  the expiry date
     * @param spotPrice   the current spot price of the index
     * @param threshold   the threshold percentage for strike range calculation
     * @return filtered list of instruments
     */
    public List<Instrument> filterInstruments(String indexSymbol, LocalDate expiryDate,
            BigDecimal spotPrice, double threshold) {
        if (indexSymbol == null || expiryDate == null || spotPrice == null) {
            return Collections.emptyList();
        }

        // Calculate strike range
        BigDecimal lowerStrike = spotPrice.multiply(BigDecimal.valueOf(1 - threshold / 100));
        BigDecimal upperStrike = spotPrice.multiply(BigDecimal.valueOf(1 + threshold / 100));

        // Build filter
        Predicate<Instrument> filter = instrument ->
        // Must be an option
        instrument.isOption() &&
        // Only include supported exchanges (NFO, BFO)
                SUPPORTED_FNO_EXCHANGES.contains(instrument.getExchange()) &&
                // Match index
                instrument.getUnderlyingSymbol().equalsIgnoreCase(indexSymbol) &&
                // Match expiry
                expiryDate.equals(instrument.getExpiryDate()) &&
                // Match strike range
                instrument.getStrikePrice().compareTo(lowerStrike) >= 0 &&
                instrument.getStrikePrice().compareTo(upperStrike) <= 0;

        // Apply filter
        return instruments.values().stream()
                .filter(filter)
                .collect(Collectors.toList());
    }

    /**
     * Download instruments from the Kite API
     */
    private void downloadInstruments() {
        LOGGER.info("Downloading only index-related instruments...");

        try {
            // Set of index symbols we're interested in - these are the ONLY ones we care
            // about
            Set<String> targetIndices = Set.of("NIFTY", "BANKNIFTY", "FINNIFTY", "SENSEX", "BANKEX");

            // First download index instruments from NSE to get spot prices
            LOGGER.info("Downloading indices from NSE...");
            try {
                List<Instrument> nseInstruments = tradingApiClient.getInstruments("NSE");
                int indexCount = 0;
                for (Instrument instrument : nseInstruments) {
                    // Only keep the main indices we're interested in
                    if (instrument.getType() == InstrumentType.INDEX &&
                            targetIndices.contains(instrument.getTradingSymbol().toUpperCase())) {
                        instruments.put(instrument.getInstrumentId(), instrument);
                        indexCount++;
                    }
                }
                LOGGER.info("Downloaded " + indexCount + " index instruments from NSE");
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error downloading index instruments from NSE", e);
            }

            // Download instruments from NFO
            List<Instrument> nfoInstruments = tradingApiClient.getInstruments("NFO");
            int nfoCount = 0;
            for (Instrument instrument : nfoInstruments) {
                // ALWAYS filter to only keep instruments related to our target indices
                if (instrument.getUnderlyingSymbol() != null &&
                        targetIndices.contains(instrument.getUnderlyingSymbol().toUpperCase())) {
                    instruments.put(instrument.getInstrumentId(), instrument);
                    nfoCount++;
                }
            }
            LOGGER.info("Downloaded " + nfoCount + " index-related instruments from NFO");

            // Download instruments from BFO
            List<Instrument> bfoInstruments = tradingApiClient.getInstruments("BFO");
            int bfoCount = 0;
            for (Instrument instrument : bfoInstruments) {
                // ALWAYS filter to only keep instruments related to our target indices
                if (instrument.getUnderlyingSymbol() != null &&
                        targetIndices.contains(instrument.getUnderlyingSymbol().toUpperCase())) {
                    instruments.put(instrument.getInstrumentId(), instrument);
                    bfoCount++;
                }
            }
            LOGGER.info("Downloaded " + bfoCount + " index-related instruments from BFO");

            // Get latest index prices
            updateIndexPrices();

            // Save to cache
            saveInstrumentsToCache();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error downloading instruments from API", e);
            throw e; // Re-throw to be handled by the caller
        }
    }

    /**
     * Refresh the instrument data from API
     */
    public void refreshInstruments() {
        // Check if we already have data files and they're not stale
        if (!areDataFilesStale()) {
            LOGGER.info("Using existing instrument data files (not stale)");
            // Add call to log expiry dates even when using cached data
            logCurrentExpiryDates();
            return;
        }

        refreshInstrumentsAndSaveToFiles();
    }

    /**
     * Refreshes all instruments from the API
     */
    public void refreshInstruments(boolean useMockData) {
        LOGGER.info("Refreshing instruments from API");

        // Clear existing instruments
        instruments.clear();

        try {
            downloadInstruments();

            // Filter to keep only index and related instruments
            filterIndexRelatedInstruments();

            // Organize data for efficient lookups
            organizeInstrumentDataForLookups();

            LOGGER.info("Instruments refreshed successfully");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error refreshing instruments", e);
            throw new RuntimeException(
                    "Failed to download instruments from API. Please check your connection and authentication status.",
                    e);
        }
    }

    /**
     * Force refresh of instruments, bypassing the stale check.
     * This will always download fresh index instruments from the API.
     */
    public void forceRefreshInstruments() {
        LOGGER.info("Forcing refresh of index instruments from API...");
        refreshInstrumentsAndSaveToFiles();
    }

    /**
     * Kept for backward compatibility - same as forceRefreshInstruments()
     * 
     * @deprecated Use forceRefreshInstruments() instead
     */
    @Deprecated
    public void forceRefreshIndexInstruments() {
        forceRefreshInstruments();
    }

    /**
     * Organizes instruments for efficient lookups
     * This pre-computes certain mappings to make filtering faster
     */
    private void organizeInstrumentDataForLookups() {
        LOGGER.info("Organizing instrument data for efficient lookups");

        // Count types of instruments
        long callOptions = instruments.values().stream()
                .filter(i -> i.getOptionType() == OptionType.CALL)
                .count();

        long putOptions = instruments.values().stream()
                .filter(i -> i.getOptionType() == OptionType.PUT)
                .count();

        long indices = instruments.values().stream()
                .filter(i -> i.getType() == InstrumentType.INDEX)
                .count();

        // Count instruments by exchange
        Map<String, Long> exchangeCounts = instruments.values().stream()
                .collect(Collectors.groupingBy(Instrument::getExchange, Collectors.counting()));

        // Count instruments by underlying
        Map<String, Long> underlyingCounts = instruments.values().stream()
                .filter(i -> i.getUnderlyingSymbol() != null)
                .collect(Collectors.groupingBy(
                        Instrument::getUnderlyingSymbol,
                        Collectors.counting()));

        // Log summary
        StringBuilder summary = new StringBuilder();
        summary.append("Instrument organization complete:\n");
        summary.append("- Total instruments: ").append(instruments.size()).append("\n");
        summary.append("- Call options: ").append(callOptions).append("\n");
        summary.append("- Put options: ").append(putOptions).append("\n");
        summary.append("- Indices: ").append(indices).append("\n");
        summary.append("- By exchange: ").append(exchangeCounts).append("\n");

        // Log top 5 underlyings
        summary.append("- Top underlyings: ");
        underlyingCounts.entrySet().stream()
                .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                .limit(5)
                .forEach(e -> summary.append(e.getKey()).append("(").append(e.getValue()).append(") "));

        LOGGER.info(summary.toString());
    }

    /**
     * Update index spot prices
     */
    private void updateIndexPrices() {
        LOGGER.info("Updating index spot prices");

        try {
            // Get indices from instruments
            List<String> indexIds = instruments.values().stream()
                    .filter(i -> i.getType() == InstrumentType.INDEX)
                    .map(Instrument::getInstrumentId)
                    .collect(Collectors.toList());

            if (indexIds.isEmpty()) {
                LOGGER.severe("No indices found to update prices - this will cause trading errors!");
                return;
            }

            // Log all indices we're looking for
            LOGGER.info("Found " + indexIds.size() + " indices to update prices: " +
                    instruments.values().stream()
                            .filter(i -> i.getType() == InstrumentType.INDEX)
                            .map(Instrument::getTradingSymbol)
                            .collect(Collectors.joining(", ")));

            // Get LTP for indices
            Map<String, BigDecimal> prices = tradingApiClient.getLTP(indexIds);

            // Check if prices is empty or null
            if (prices == null || prices.isEmpty()) {
                LOGGER.severe("Received empty price data from API - this will cause trading errors!");
                return;
            }

            // Update index spot prices
            for (Map.Entry<String, BigDecimal> entry : prices.entrySet()) {
                String instrumentId = entry.getKey();
                Instrument instrument = instruments.get(instrumentId);

                if (instrument != null) {
                    // For INDEX type instruments, use the tradingSymbol as the key
                    // This is the actual index name (e.g., "NIFTY", "BANKNIFTY")
                    String symbol = instrument.getTradingSymbol();
                    BigDecimal price = entry.getValue();

                    if (symbol != null && price != null) {
                        indexSpotPrices.put(symbol.toUpperCase(), price);
                        LOGGER.info("Updated index price: " + symbol + " = " + price);
                    } else {
                        LOGGER.warning("Missing symbol or price for instrument: " + instrumentId);
                    }
                } else {
                    LOGGER.warning("Received price for unknown instrument: " + instrumentId);
                }
            }

            // Verify we have prices for main indices
            List<String> mainIndices = Arrays.asList("NIFTY", "BANKNIFTY", "FINNIFTY", "SENSEX");
            for (String index : mainIndices) {
                if (!indexSpotPrices.containsKey(index)) {
                    LOGGER.severe("Missing price for important index: " + index + " - this may cause trading errors!");
                }
            }

            // Log all index prices to verify
            LOGGER.info("Current index prices: " + indexSpotPrices);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error updating index prices", e);
        }
    }

    /**
     * Save instruments to cache
     */
    private void saveInstrumentsToCache() {
        try {
            cacheManager.createCache("instruments", 1, 86400000); // 24 hour TTL
            cacheManager.getCache("instruments").put("all", new HashMap<>(instruments));
            LOGGER.info("Saved " + instruments.size() + " instruments to cache");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error saving instruments to cache", e);
        }
    }

    /**
     * Load instruments from cache
     * 
     * @return the cached instruments or null if not found
     */
    @SuppressWarnings("unchecked")
    private Map<String, Instrument> loadInstrumentsFromCache() {
        try {
            return (Map<String, Instrument>) cacheManager.getCache("instruments").get("all");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error loading instruments from cache", e);
            return null;
        }
    }

    /**
     * Get a list of all instrument IDs from the instruments
     * This is a helper for cache iteration
     * 
     * @return set of instrument IDs
     */
    private Set<String> instrumentIds() {
        return instruments.keySet();
    }

    /**
     * Check if cache is stale based on last updated date
     * 
     * @param lastUpdated the last updated date as string
     * @return true if cache is stale
     */
    private boolean isCacheStale(String lastUpdated) {
        if (lastUpdated == null) {
            return true;
        }

        try {
            LocalDate lastUpdateDate = LocalDate.parse(lastUpdated);
            return !lastUpdateDate.equals(LocalDate.now());
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error parsing last updated date: " + lastUpdated, e);
            return true;
        }
    }

    /**
     * Check if instruments cache is stale (older than today)
     * 
     * @return true if cache is stale or doesn't exist
     */
    public boolean isCacheStale() {
        try {
            if (!cacheManager.hasCache("instruments_metadata")) {
                return true;
            }

            CacheManager.BoundedCache<String> metadataCache = cacheManager.getCache("instruments_metadata");
            String lastUpdated = metadataCache.get("last_updated");

            return isCacheStale(lastUpdated);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking if cache is stale", e);
            return true;
        }
    }

    /**
     * Pre-filter instruments based only on index and expiry
     * This is an optimization to reduce processing time during order execution
     * 
     * @param indexSymbol the index symbol (e.g., "NIFTY")
     * @param expiryDate  the expiry date
     * @return filtered list of instruments
     */
    public List<Instrument> preFilterInstruments(String indexSymbol, LocalDate expiryDate) {
        if (indexSymbol == null || expiryDate == null) {
            return Collections.emptyList();
        }

        LOGGER.info("Pre-filtering instruments for " + indexSymbol + " with expiry " + expiryDate);

        // Build filter
        Predicate<Instrument> filter = instrument ->
        // Must be an option
        instrument.isOption() &&
        // Only include supported exchanges (NFO, BFO)
                SUPPORTED_FNO_EXCHANGES.contains(instrument.getExchange()) &&
                // Match index
                instrument.getUnderlyingSymbol().equalsIgnoreCase(indexSymbol) &&
                // Match expiry
                expiryDate.equals(instrument.getExpiryDate());

        // Apply filter
        List<Instrument> filtered = instruments.values().stream()
                .filter(filter)
                .collect(Collectors.toList());

        LOGGER.info("Pre-filtered to " + filtered.size() + " instruments for " + indexSymbol + " with expiry "
                + expiryDate);
        return filtered;
    }

    /**
     * Further filter pre-filtered instruments based on strike range
     * This is used at T-25s when we know the current spot price
     * 
     * @param preFilteredInstruments instruments already filtered by index and
     *                               expiry
     * @param spotPrice              current spot price of the index
     * @param threshold              the threshold for strike range calculation
     * @return list of instruments within the strike range
     */
    public List<Instrument> applyStrikeFilter(List<Instrument> preFilteredInstruments,
            BigDecimal spotPrice,
            double threshold) {
        if (preFilteredInstruments == null || preFilteredInstruments.isEmpty() || spotPrice == null) {
            return Collections.emptyList();
        }

        LOGGER.info("Filtering " + preFilteredInstruments.size() +
                " instruments by strike range around " + spotPrice +
                " with threshold " + threshold + "%");

        // Calculate strike range
        BigDecimal lowerStrike = spotPrice.multiply(BigDecimal.valueOf(1 - threshold / 100));
        BigDecimal upperStrike = spotPrice.multiply(BigDecimal.valueOf(1 + threshold / 100));

        // Apply strike filter
        List<Instrument> filteredInstruments = preFilteredInstruments.stream()
                .filter(instrument -> instrument.getStrikePrice().compareTo(lowerStrike) >= 0 &&
                        instrument.getStrikePrice().compareTo(upperStrike) <= 0)
                .collect(Collectors.toList());

        LOGGER.info("Selected " + filteredInstruments.size() +
                " instruments within strike range " + lowerStrike + " to " + upperStrike);
        return filteredInstruments;
    }

    /**
     * Save instruments to files organized by underlying symbol and expiry date
     */
    private void saveInstrumentsToFiles() {
        LOGGER.info("Saving instruments to files in " + INSTRUMENTS_DIR);

        try {
            // Create directory if it doesn't exist
            File instrumentsDir = new File(INSTRUMENTS_DIR);
            if (!instrumentsDir.exists()) {
                instrumentsDir.mkdirs();
            }

            // Save metadata file with last update timestamp
            File metadataFile = new File(INSTRUMENTS_DIR + "/metadata.properties");
            Properties metadata = new Properties();
            metadata.setProperty("last_updated", LocalDate.now().toString());
            metadata.setProperty("total_instruments", String.valueOf(instruments.size()));

            try (FileOutputStream fos = new FileOutputStream(metadataFile)) {
                metadata.store(fos, "Instruments metadata");
            }

            // Group instruments by underlying symbol
            Map<String, List<Instrument>> byUnderlying = instruments.values().stream()
                    .filter(i -> i.getUnderlyingSymbol() != null)
                    .collect(Collectors.groupingBy(Instrument::getUnderlyingSymbol));

            // For each underlying symbol
            for (Map.Entry<String, List<Instrument>> entry : byUnderlying.entrySet()) {
                String symbol = entry.getKey();
                List<Instrument> symbolInstruments = entry.getValue();

                // Create directory for this symbol
                File symbolDir = new File(INSTRUMENTS_DIR + "/" + symbol);
                if (!symbolDir.exists()) {
                    symbolDir.mkdirs();
                }

                // Group by expiry date
                Map<LocalDate, List<Instrument>> byExpiry = symbolInstruments.stream()
                        .filter(i -> i.getExpiryDate() != null)
                        .collect(Collectors.groupingBy(Instrument::getExpiryDate));

                // For each expiry date
                for (Map.Entry<LocalDate, List<Instrument>> expiryEntry : byExpiry.entrySet()) {
                    LocalDate expiry = expiryEntry.getKey();
                    List<Instrument> expiryInstruments = expiryEntry.getValue();

                    // Save to file named by expiry date
                    String filename = expiry.toString() + ".dat";
                    File expiryFile = new File(symbolDir, filename);

                    try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(expiryFile))) {
                        oos.writeObject(new ArrayList<>(expiryInstruments));
                    }
                }

                // Save a list of available expiry dates for this symbol
                List<LocalDate> expiryDates = new ArrayList<>(byExpiry.keySet());
                Collections.sort(expiryDates);

                File expiryListFile = new File(symbolDir, "expiries.dat");
                try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(expiryListFile))) {
                    oos.writeObject(expiryDates);
                }
            }

            // Save a list of all underlying symbols
            List<String> allSymbols = new ArrayList<>(byUnderlying.keySet());
            Collections.sort(allSymbols);

            File symbolsFile = new File(INSTRUMENTS_DIR, "symbols.dat");
            try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(symbolsFile))) {
                oos.writeObject(allSymbols);
            }

            LOGGER.info("Successfully saved instruments to files");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error saving instruments to files", e);
        }
    }

    /**
     * Load available underlying symbols from file
     * 
     * @return list of underlying symbols or empty list if file doesn't exist
     */
    @SuppressWarnings("unchecked")
    public List<String> loadAvailableSymbols() {
        File symbolsFile = new File(INSTRUMENTS_DIR, "symbols.dat");
        if (!symbolsFile.exists()) {
            return Collections.emptyList();
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(symbolsFile))) {
            return (List<String>) ois.readObject();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error loading symbols file", e);
            return Collections.emptyList();
        }
    }

    /**
     * Load available expiry dates for a symbol from file
     * 
     * @param symbol the underlying symbol
     * @return list of expiry dates or empty list if file doesn't exist
     */
    @SuppressWarnings("unchecked")
    public List<LocalDate> loadAvailableExpiries(String symbol) {
        File expiryListFile = new File(INSTRUMENTS_DIR + "/" + symbol, "expiries.dat");
        if (!expiryListFile.exists()) {
            return Collections.emptyList();
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(expiryListFile))) {
            return (List<LocalDate>) ois.readObject();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error loading expiry list for " + symbol, e);
            return Collections.emptyList();
        }
    }

    /**
     * Load instruments for a specific underlying symbol and expiry date
     * 
     * @param symbol the underlying symbol
     * @param expiry the expiry date
     * @return list of instruments or empty list if file doesn't exist
     */
    @SuppressWarnings("unchecked")
    public List<Instrument> loadInstrumentsForSymbolAndExpiry(String symbol, LocalDate expiry) {
        String filename = expiry.toString() + ".dat";
        File expiryFile = new File(INSTRUMENTS_DIR + "/" + symbol, filename);
        if (!expiryFile.exists()) {
            return Collections.emptyList();
        }

        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(expiryFile))) {
            List<Instrument> loadedInstruments = (List<Instrument>) ois.readObject();

            // Add to runtime cache
            for (Instrument instrument : loadedInstruments) {
                instruments.put(instrument.getInstrumentId(), instrument);
            }

            return loadedInstruments;
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error loading instruments for " + symbol + " expiry " + expiry, e);
            return Collections.emptyList();
        }
    }

    /**
     * Check if instruments data files are stale (older than today)
     * 
     * @return true if data files are stale or don't exist
     */
    public boolean areDataFilesStale() {
        File metadataFile = new File(INSTRUMENTS_DIR + "/metadata.properties");
        if (!metadataFile.exists()) {
            return true;
        }

        try {
            Properties metadata = new Properties();
            try (FileInputStream fis = new FileInputStream(metadataFile)) {
                metadata.load(fis);
            }

            String lastUpdated = metadata.getProperty("last_updated");
            return isCacheStale(lastUpdated);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error checking if data files are stale", e);
            return true;
        }
    }

    /**
     * Refresh the instrument data from API and save to files
     */
    public void refreshInstrumentsAndSaveToFiles() {
        LOGGER.info("Refreshing instruments from API and saving to files");

        // Clear existing instruments
        instruments.clear();

        try {
            downloadInstruments();

            // Organize data for efficient lookups
            organizeInstrumentDataForLookups();

            // Log expiry dates for main indices
            logExpiryDatesForMainIndices();

            // Save to files
            saveInstrumentsToFiles();

            // Also save to cache for current session
            saveInstrumentsToCache();

            LOGGER.info("Successfully loaded and saved " + instruments.size() + " instruments");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error refreshing instruments", e);
            throw new RuntimeException(
                    "Failed to download instruments from API. Please check your connection and authentication status.",
                    e);
        }
    }

    /**
     * Log available expiry dates for main indices
     */
    private void logExpiryDatesForMainIndices() {
        String[] mainIndices = { "NIFTY", "BANKNIFTY", "FINNIFTY", "SENSEX", "BANKEX" };

        for (String index : mainIndices) {
            List<LocalDate> expiryDates = getAllOptionInstruments().stream()
                    .filter(i -> index.equals(i.getUnderlyingSymbol()))
                    .map(Instrument::getExpiryDate)
                    .filter(Objects::nonNull) // Filter out null expiry dates
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList());

            if (!expiryDates.isEmpty()) {
                StringBuilder sb = new StringBuilder();
                sb.append("Available expiry dates for ").append(index).append(": ");

                for (LocalDate date : expiryDates) {
                    // Date is guaranteed to be non-null here
                    sb.append(date.format(java.time.format.DateTimeFormatter.ofPattern("dd-MMM-yyyy"))).append(", ");
                }

                // Remove trailing comma and space
                if (sb.length() > 2) {
                    sb.setLength(sb.length() - 2);
                }

                LOGGER.info(sb.toString());
            } else {
                LOGGER.info("No expiry dates found for " + index);
            }
        }

        // Log a warning if we received instruments with null expiry dates
        long nullExpiryCount = getAllOptionInstruments().stream()
                .filter(i -> i.getExpiryDate() == null)
                .count();

        if (nullExpiryCount > 0) {
            LOGGER.warning("Found " + nullExpiryCount + " option instruments with NULL expiry dates");
        }
    }

    /**
     * Logs expiry dates for main indices from currently loaded instruments.
     * This method can be called at any time to output currently available expiry
     * dates
     * without requiring a fresh download of instrument data.
     */
    public void logCurrentExpiryDates() {
        LOGGER.info("Loading instruments from cached files...");

        // Load cached instruments if the map is empty
        if (instruments.isEmpty()) {
            String[] mainIndices = { "NIFTY", "BANKNIFTY", "FINNIFTY", "SENSEX", "BANKEX" };
            for (String symbol : mainIndices) {
                List<LocalDate> expiries = loadAvailableExpiries(symbol);
                if (expiries != null && !expiries.isEmpty()) {
                    for (LocalDate expiry : expiries) {
                        loadInstrumentsForSymbolAndExpiry(symbol, expiry);
                    }
                }
            }
        }

        // Now log expiry dates from the loaded data
        LOGGER.info("Logging expiry dates from currently loaded instruments...");
        logExpiryDatesForMainIndices();
    }

    /**
     * Filter to keep only index and related instruments
     */
    private void filterIndexRelatedInstruments() {
        Set<String> targetIndices = new HashSet<>(Arrays.asList("NIFTY", "BANKNIFTY", "FINNIFTY", "SENSEX", "BANKEX"));

        // Count before filtering
        int beforeCount = instruments.size();
        LOGGER.info("Total instruments before filtering: " + beforeCount);

        // First, keep all INDEX type instruments regardless of symbol
        // (we'll need these for price lookups)
        Map<String, Instrument> filteredInstruments = instruments.values().stream()
                .filter(i -> i.getType() == InstrumentType.INDEX ||
                        (i.getUnderlyingSymbol() != null &&
                                targetIndices.contains(i.getUnderlyingSymbol().toUpperCase())))
                .collect(Collectors.toMap(Instrument::getInstrumentId, i -> i));

        // Log and store the filtered instruments
        int afterCount = filteredInstruments.size();
        LOGGER.info("Kept " + afterCount + " index-related instruments out of " + beforeCount + " total");

        // Clear and add all filtered instruments
        instruments.clear();
        instruments.putAll(filteredInstruments);

        // Log all kept indices to verify
        List<String> keptIndices = instruments.values().stream()
                .filter(i -> i.getType() == InstrumentType.INDEX)
                .map(i -> i.getTradingSymbol() + " (" + i.getInstrumentId() + ")")
                .collect(Collectors.toList());
        LOGGER.info("Kept indices: " + keptIndices);
    }

    /**
     * Gets the last Thursday of the specified month
     */
    private LocalDate getLastThursday(int year, int month) {
        // Handle month overflow
        if (month > 12) {
            year += month / 12;
            month = month % 12;
            if (month == 0) {
                month = 12;
                year--;
            }
        }

        LocalDate lastDay = Year.of(year).atMonth(month).atEndOfMonth();
        LocalDate lastThursday = lastDay;

        while (lastThursday.getDayOfWeek() != DayOfWeek.THURSDAY) {
            lastThursday = lastThursday.minusDays(1);
        }

        return lastThursday;
    }

    /**
     * Find options at a specific strike price
     * 
     * @param underlyingSymbol The underlying symbol (e.g., "NIFTY")
     * @param expiryDate       The expiry date
     * @param strikePrice      The strike price to look for
     * @param optionType       CALL or PUT
     * @return List of matching instruments
     */
    public List<Instrument> findOptionsAtStrike(String underlyingSymbol, LocalDate expiryDate,
            BigDecimal strikePrice, OptionType optionType) {
        LOGGER.info("Finding options for " + underlyingSymbol + " expiry " + expiryDate +
                " at strike " + strikePrice + " type " + optionType);

        if (instruments.isEmpty()) {
            LOGGER.warning("No instruments loaded, cannot find options");
            return Collections.emptyList();
        }

        // Build filter
        Predicate<Instrument> filter = instrument -> instrument.isOption() &&
                instrument.getOptionType() == optionType &&
                SUPPORTED_FNO_EXCHANGES.contains(instrument.getExchange()) &&
                instrument.getUnderlyingSymbol() != null &&
                instrument.getUnderlyingSymbol().equalsIgnoreCase(underlyingSymbol) &&
                expiryDate.equals(instrument.getExpiryDate()) &&
                instrument.getStrikePrice() != null &&
                instrument.getStrikePrice().compareTo(strikePrice) == 0;

        // Apply filter
        List<Instrument> result = instruments.values().stream()
                .filter(filter)
                .collect(Collectors.toList());

        LOGGER.info("Found " + result.size() + " options at strike " + strikePrice);

        // If no exact match, try to find the closest strike
        if (result.isEmpty()) {
            LOGGER.info("No exact match, finding closest strike");

            // Relaxed filter without strike price constraint
            Predicate<Instrument> relaxedFilter = instrument -> instrument.isOption() &&
                    instrument.getOptionType() == optionType &&
                    SUPPORTED_FNO_EXCHANGES.contains(instrument.getExchange()) &&
                    instrument.getUnderlyingSymbol() != null &&
                    instrument.getUnderlyingSymbol().equalsIgnoreCase(underlyingSymbol) &&
                    expiryDate.equals(instrument.getExpiryDate()) &&
                    instrument.getStrikePrice() != null;

            // Find all options for this expiry and type
            List<Instrument> allOptions = instruments.values().stream()
                    .filter(relaxedFilter)
                    .collect(Collectors.toList());

            if (!allOptions.isEmpty()) {
                // Find the closest strike
                Instrument closestOption = allOptions.stream()
                        .min((a, b) -> {
                            BigDecimal diffA = a.getStrikePrice().subtract(strikePrice).abs();
                            BigDecimal diffB = b.getStrikePrice().subtract(strikePrice).abs();
                            return diffA.compareTo(diffB);
                        })
                        .orElse(null);

                if (closestOption != null) {
                    LOGGER.info("Found closest option at strike " + closestOption.getStrikePrice() +
                            " instead of requested " + strikePrice);
                    return Collections.singletonList(closestOption);
                }
            }
        }

        return result;
    }
}