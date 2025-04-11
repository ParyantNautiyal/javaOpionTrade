package com.optiontrading.service.instrument;

import com.optiontrading.events.EventBus;
import com.optiontrading.resources.CacheManager;
import com.optiontrading.resources.ResourceManager;
import com.optiontrading.service.api.KiteConnectClient;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.InstrumentType;
import com.optiontrading.service.model.OptionType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;

/**
 * Service for downloading and managing instruments
 */
public class InstrumentService {
    private static final Logger LOGGER = Logger.getLogger(InstrumentService.class.getName());
    private static InstrumentService INSTANCE;

    // Cache of all instruments
    private final Map<String, Instrument> instruments = new ConcurrentHashMap<>();

    // Map of index to current spot price
    private final Map<String, BigDecimal> indexSpotPrices = new ConcurrentHashMap<>();

    // Supported exchanges for F&O instruments
    private static final Set<String> SUPPORTED_FNO_EXCHANGES = Set.of("NFO", "BFO");

    // Resource managers and services
    private final CacheManager cacheManager;
    private final EventBus eventBus;
    private final KiteConnectClient kiteClient;

    /**
     * Constructor for dependency injection
     */
    public InstrumentService(CacheManager cacheManager, EventBus eventBus, KiteConnectClient kiteClient) {
        this.cacheManager = cacheManager;
        this.eventBus = eventBus;
        this.kiteClient = kiteClient;

        // Don't load instruments automatically - wait until refreshInstruments is
        // called
        LOGGER.info("Created InstrumentService with dependency injection (initialization deferred)");
    }

    // Private constructor for singleton
    private InstrumentService() {
        this.cacheManager = ResourceManager.getInstance().getCacheManager();
        this.eventBus = EventBus.getInstance();
        this.kiteClient = KiteConnectClient.getInstance();

        // Don't load instruments automatically - wait until refreshInstruments is
        // called
        LOGGER.info("Created InstrumentService singleton (initialization deferred)");
    }

    /**
     * Get the singleton instance
     */
    public static synchronized InstrumentService getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new InstrumentService();
        }
        return INSTANCE;
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
     * Initialize with mock data for testing
     */
    private void initializeMockData() {
        LOGGER.info("Initializing mock instrument data");

        // Clear existing instruments
        instruments.clear();

        // Set up some indices
        indexSpotPrices.put("NIFTY", BigDecimal.valueOf(19500.0));
        indexSpotPrices.put("BANKNIFTY", BigDecimal.valueOf(45000.0));
        indexSpotPrices.put("FINNIFTY", BigDecimal.valueOf(22000.0));
        indexSpotPrices.put("SENSEX", BigDecimal.valueOf(65000.0));
        indexSpotPrices.put("BANKEX", BigDecimal.valueOf(48000.0));

        // Create some expiry dates
        LocalDate currentDate = LocalDate.now();
        LocalDate weeklyExpiry = currentDate.plusDays((4 - currentDate.getDayOfWeek().getValue() + 7) % 7);
        LocalDate monthlyExpiry = currentDate.withDayOfMonth(currentDate.lengthOfMonth());

        // Generate mock instruments for NFO exchange
        createMockOptionsChain("NIFTY", weeklyExpiry, BigDecimal.valueOf(19500.0), 100, 5, "NFO");
        createMockOptionsChain("NIFTY", monthlyExpiry, BigDecimal.valueOf(19500.0), 100, 10, "NFO");
        createMockOptionsChain("BANKNIFTY", weeklyExpiry, BigDecimal.valueOf(45000.0), 100, 10, "NFO");
        createMockOptionsChain("BANKNIFTY", monthlyExpiry, BigDecimal.valueOf(45000.0), 100, 15, "NFO");
        createMockOptionsChain("FINNIFTY", weeklyExpiry, BigDecimal.valueOf(22000.0), 50, 5, "NFO");

        // Generate a few mock instruments for BFO exchange
        createMockOptionsChain("SENSEX", weeklyExpiry, BigDecimal.valueOf(65000.0), 500, 5, "BFO");
        createMockOptionsChain("BANKEX", weeklyExpiry, BigDecimal.valueOf(48000.0), 500, 5, "BFO");

        LOGGER.info("Created " + instruments.size() + " mock instruments");
    }

    /**
     * Create a mock options chain for testing
     * 
     * @param indexSymbol the index symbol
     * @param expiryDate  the expiry date
     * @param spotPrice   the spot price
     * @param strikeStep  the step between strike prices
     * @param numStrikes  the number of strikes to generate (above and below spot)
     * @param exchange    the exchange (NFO or BFO)
     */
    private void createMockOptionsChain(String indexSymbol, LocalDate expiryDate,
            BigDecimal spotPrice, int strikeStep, int numStrikes, String exchange) {
        // Round spot price to nearest strikeStep
        BigDecimal baseStrike = BigDecimal.valueOf(
                Math.round(spotPrice.doubleValue() / strikeStep) * strikeStep);

        // Generate strikes above and below spot
        for (int i = -numStrikes; i <= numStrikes; i++) {
            BigDecimal strike = baseStrike.add(BigDecimal.valueOf(i * strikeStep));

            // Create CALL option
            String callId = exchange + ":" + indexSymbol + expiryDate.toString().replace("-", "") + "C"
                    + strike.intValue();
            Instrument callOption = Instrument.builder()
                    .instrumentId(callId)
                    .tradingSymbol(indexSymbol + expiryDate.toString().replace("-", "") + "C" + strike.intValue())
                    .exchange(exchange)
                    .type(InstrumentType.OPTION)
                    .underlyingSymbol(indexSymbol)
                    .expiryDate(expiryDate)
                    .strikePrice(strike)
                    .optionType(OptionType.CALL)
                    .lotSize(exchange.equals("NFO") ? 50 : 10) // Different lot sizes for different exchanges
                    .build();

            instruments.put(callId, callOption);

            // Create PUT option
            String putId = exchange + ":" + indexSymbol + expiryDate.toString().replace("-", "") + "P"
                    + strike.intValue();
            Instrument putOption = Instrument.builder()
                    .instrumentId(putId)
                    .tradingSymbol(indexSymbol + expiryDate.toString().replace("-", "") + "P" + strike.intValue())
                    .exchange(exchange)
                    .type(InstrumentType.OPTION)
                    .underlyingSymbol(indexSymbol)
                    .expiryDate(expiryDate)
                    .strikePrice(strike)
                    .optionType(OptionType.PUT)
                    .lotSize(exchange.equals("NFO") ? 50 : 10) // Different lot sizes for different exchanges
                    .build();

            instruments.put(putId, putOption);
        }
    }

    /**
     * Refresh instruments from API or use cached data
     * This should be called at application startup
     * 
     * @param forceRefresh true to force refresh from API, false to check cache
     *                     first
     * @return true if refresh was successful
     */
    public boolean refreshInstruments(boolean forceRefresh) {
        LOGGER.info("Refreshing instruments (force=" + forceRefresh + ")");

        // Check if we're authenticated
        if (!kiteClient.isAuthenticated()) {
            LOGGER.warning("Cannot download instruments - not authenticated with Kite API");
            LOGGER.info("Using mock data instead");
            initializeMockData();
            return false;
        }

        // Check cache if not forcing refresh
        if (!forceRefresh) {
            try {
                Map<String, Instrument> cachedInstruments = loadInstrumentsFromCache();
                if (cachedInstruments != null && !cachedInstruments.isEmpty()) {
                    LOGGER.info("Loaded " + cachedInstruments.size() + " instruments from cache");
                    instruments.clear();
                    instruments.putAll(cachedInstruments);

                    // Get latest index prices
                    updateIndexPrices();

                    return true;
                } else {
                    LOGGER.info("No cached instruments found or cache is empty");
                }
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error loading instruments from cache", e);
            }
        }

        // Download fresh data
        try {
            LOGGER.info("Downloading instruments from NFO and BFO exchanges...");
            instruments.clear();

            // Download instruments from NFO
            List<Instrument> nfoInstruments = kiteClient.getInstruments("NFO");
            int nfoCount = 0;
            for (Instrument instrument : nfoInstruments) {
                instruments.put(instrument.getInstrumentId(), instrument);
                nfoCount++;
            }
            LOGGER.info("Downloaded " + nfoCount + " instruments from NFO");

            // Download instruments from BFO
            List<Instrument> bfoInstruments = kiteClient.getInstruments("BFO");
            int bfoCount = 0;
            for (Instrument instrument : bfoInstruments) {
                instruments.put(instrument.getInstrumentId(), instrument);
                bfoCount++;
            }
            LOGGER.info("Downloaded " + bfoCount + " instruments from BFO");

            LOGGER.info("Total instruments: " + instruments.size());

            // Save to cache
            saveInstrumentsToCache();

            // Get latest index prices
            updateIndexPrices();

            return true;
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error downloading instruments", e);
            LOGGER.info("Using mock data instead");
            initializeMockData();
            return false;
        }
    }

    /**
     * Save instruments to cache
     */
    private void saveInstrumentsToCache() {
        try {
            LOGGER.info("Saving " + instruments.size() + " instruments to cache");

            // Create or get the instruments cache
            if (!cacheManager.hasCache("instruments")) {
                // Large max size, no TTL
                cacheManager.createCache("instruments", 50000);
            }

            if (!cacheManager.hasCache("instruments_metadata")) {
                // Small cache for metadata, no TTL
                cacheManager.createCache("instruments_metadata", 100);
            }

            // Get the caches
            CacheManager.BoundedCache<Object> instrumentsCache = cacheManager.getCache("instruments");
            CacheManager.BoundedCache<Object> metadataCache = cacheManager.getCache("instruments_metadata");

            // Clear existing data
            instrumentsCache.clear();

            // Save each instrument
            for (Map.Entry<String, Instrument> entry : instruments.entrySet()) {
                instrumentsCache.put(entry.getKey(), entry.getValue());
            }

            // Save metadata
            metadataCache.put("last_updated", LocalDate.now().toString());

            LOGGER.info("Instruments saved to cache successfully");
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error saving instruments to cache", e);
        }
    }

    /**
     * Load instruments from cache
     * 
     * @return the cached instruments or null if not available
     */
    private Map<String, Instrument> loadInstrumentsFromCache() {
        try {
            if (!cacheManager.hasCache("instruments") || !cacheManager.hasCache("instruments_metadata")) {
                LOGGER.info("No instrument cache exists");
                return null;
            }

            // Get the caches
            CacheManager.BoundedCache<Instrument> instrumentsCache = cacheManager.getCache("instruments");
            CacheManager.BoundedCache<String> metadataCache = cacheManager.getCache("instruments_metadata");

            // Check if we have metadata
            String lastUpdated = metadataCache.get("last_updated");
            if (lastUpdated == null) {
                LOGGER.info("No last_updated timestamp in cache");
                return null;
            }

            // Check for staleness
            if (isCacheStale(lastUpdated)) {
                LOGGER.info("Cached instruments are stale (last updated: " + lastUpdated + ")");
                return null;
            }

            // Build a map of all instruments in the cache
            Map<String, Instrument> cachedInstruments = new HashMap<>();

            // We need to iterate through the entire cache with keys
            int count = 0;
            for (String key : instrumentIds()) {
                Instrument instrument = instrumentsCache.get(key);
                if (instrument != null) {
                    cachedInstruments.put(key, instrument);
                    count++;
                }
            }

            LOGGER.info("Loaded " + count + " instruments from cache");
            return cachedInstruments;
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
     * Update index prices from market data
     */
    private void updateIndexPrices() {
        try {
            LOGGER.info("Updating index prices");

            // List of index instruments to get prices for
            List<String> indices = Arrays.asList(
                    "NSE:NIFTY50",
                    "NSE:BANKNIFTY",
                    "NSE:FINNIFTY");

            // Get current prices
            Map<String, BigDecimal> prices = kiteClient.getLTP(indices);

            // Update our index prices
            if (prices.containsKey("NSE:NIFTY50")) {
                indexSpotPrices.put("NIFTY", prices.get("NSE:NIFTY50"));
            }

            if (prices.containsKey("NSE:BANKNIFTY")) {
                indexSpotPrices.put("BANKNIFTY", prices.get("NSE:BANKNIFTY"));
            }

            if (prices.containsKey("NSE:FINNIFTY")) {
                indexSpotPrices.put("FINNIFTY", prices.get("NSE:FINNIFTY"));
            }

            LOGGER.info("Updated index prices: " + indexSpotPrices);
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error updating index prices", e);
        }
    }

    /**
     * Refresh instruments from API
     * Legacy method that calls refreshInstruments(true)
     */
    public void refreshInstruments() {
        refreshInstruments(true);
    }
}