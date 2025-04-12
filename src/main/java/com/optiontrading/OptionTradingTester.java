package com.optiontrading;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.optiontrading.di.AppModule;
import com.optiontrading.resources.ResourceManager;
import com.optiontrading.service.auth.AuthService;
import com.optiontrading.service.market.MarketDataService;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OrderType;
import com.optiontrading.service.position.PositionWatchlistService;
import com.optiontrading.service.position.WatchedPosition;
import com.optiontrading.service.api.TradingApiClient;
import com.optiontrading.service.instrument.InstrumentService;

import java.math.BigDecimal;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.logging.Logger;
import java.io.File;

/**
 * Main entry point for the option trading test application
 */
public class OptionTradingTester {
    private static final Logger LOGGER = Logger.getLogger(OptionTradingTester.class.getName());

    // Services injected by Guice
    private final ResourceManager resourceManager;
    private final AuthService authService;
    private final MarketDataService marketDataService;
    private final PositionWatchlistService positionWatchlistService;
    private final TradingApiClient tradingApiClient;

    /**
     * Constructor with dependency injection
     */
    @Inject
    public OptionTradingTester(ResourceManager resourceManager,
            AuthService authService,
            MarketDataService marketDataService,
            PositionWatchlistService positionWatchlistService,
            TradingApiClient tradingApiClient) {
        this.resourceManager = resourceManager;
        this.authService = authService;
        this.marketDataService = marketDataService;
        this.positionWatchlistService = positionWatchlistService;
        this.tradingApiClient = tradingApiClient;
    }

    /**
     * Main entry point
     * 
     * @param args command line arguments
     */
    public static void main(String[] args) {
        System.out.println("\n============================================================");
        System.out.println("           OPTION TRADING APPLICATION TESTER");
        System.out.println("============================================================");
        System.out.println("Initializing application...");

        // Create Guice injector and get main application instance
        Injector injector = Guice.createInjector(new AppModule());
        OptionTradingTester app = injector.getInstance(OptionTradingTester.class);

        // Start the application
        app.start();
    }

    /**
     * Start the application
     */
    public void start() {
        try {
            // Check authentication
            authenticate();

            // Load or download instruments as needed
            loadInstruments();

            // Start market data service
            marketDataService.start();

            // Print resources
            System.out.println(resourceManager.getResourceMetrics());

            // Test market data updates
            testMarketData();

            // Wait for user input
            System.out.println("\nPress ENTER to exit...");
            new Scanner(System.in).nextLine();

        } catch (Exception e) {
            LOGGER.severe("Error starting application: " + e.getMessage());
            e.printStackTrace();
        } finally {
            // Shutdown resources
            resourceManager.shutdown();
        }
    }

    /**
     * Ensure user is authenticated
     */
    private void authenticate() {
        System.out.println("\nAuthentication required before continuing...");

        if (!authService.isAuthenticated()) {
            System.out.println("\n===== AUTHENTICATION REQUIRED =====");

            try {
                // Check for API credentials
                if (!authService.hasCredentials()) {
                    // Collect credentials interactively
                    Scanner scanner = new Scanner(System.in);
                    System.out.println("API credentials not found. Please enter your Kite API credentials:");

                    System.out.print("API Key: ");
                    String apiKey = scanner.nextLine().trim();

                    System.out.print("API Secret: ");
                    String apiSecret = scanner.nextLine().trim();

                    // Save API credentials
                    authService.saveCredentials(apiKey, apiSecret);
                }

                // Get authentication URL
                String authUrl = authService.getLoginUrl();
                System.out.println("\nPlease visit the following URL to authenticate:");
                System.out.println(authUrl);

                // Get request token from user
                System.out.print("\nEnter the request token from the redirect URL: ");
                String requestToken = new Scanner(System.in).nextLine().trim();

                // Generate access token
                authService.generateAccessToken(requestToken);

                System.out.println("Authentication successful!");
            } catch (Exception e) {
                System.err.println("Authentication error: " + e.getMessage());
                throw new RuntimeException("Authentication failed", e);
            }
        } else {
            System.out.println("Already authenticated.");
        }
    }

    /**
     * Load instruments from cache or download if needed
     */
    private void loadInstruments() {
        System.out.println("\n===== LOADING INSTRUMENTS =====");

        // Get the instrument service using Guice
        InstrumentService instrumentService = Guice.createInjector(new AppModule())
                .getInstance(InstrumentService.class);

        // Check if data/instruments directory exists and has content
        File instrumentsDir = new File("data/instruments");
        File metadataFile = new File("data/instruments/metadata.properties");

        if (!instrumentsDir.exists() || !instrumentsDir.isDirectory() ||
                instrumentsDir.list() == null || instrumentsDir.list().length == 0 ||
                !metadataFile.exists()) {
            // First time startup - need to download instruments
            System.out.println("No instrument data found. Downloading instruments...");
            instrumentService.refreshInstrumentsAndSaveToFiles();
            System.out.println("Instruments downloaded successfully.");
        } else {
            // Use cached data
            System.out.println("Using cached instrument data from: " + instrumentsDir.getAbsolutePath());

            // Make sure we have instruments loaded in memory
            if (instrumentService.getAllInstruments().isEmpty()) {
                instrumentService.logCurrentExpiryDates();
            }
        }
    }

    /**
     * Test market data functionality
     */
    private void testMarketData() {
        System.out.println("\n===== TESTING MARKET DATA =====");

        // Example instrument IDs for index symbols
        String niftyIndex = "NSE:NIFTY 50";
        String bankNiftyIndex = "NSE:NIFTY BANK";
        String sensexIndex = "BSE:SENSEX";

        try {
            // First print current actual prices from the market
            System.out.println("Current Market Prices:");

            // Get prices from the API
            List<String> indexSymbols = Arrays.asList(niftyIndex, bankNiftyIndex, sensexIndex);
            Map<String, BigDecimal> actualPrices = tradingApiClient.getLTP(indexSymbols);

            if (actualPrices != null && !actualPrices.isEmpty()) {
                System.out.println("NIFTY 50: " + actualPrices.getOrDefault(niftyIndex, BigDecimal.ZERO));
                System.out.println("NIFTY BANK: " + actualPrices.getOrDefault(bankNiftyIndex, BigDecimal.ZERO));
                System.out.println("SENSEX: " + actualPrices.getOrDefault(sensexIndex, BigDecimal.ZERO));
            } else {
                System.out.println("Could not fetch real-time prices from the market.");
            }

            System.out.println("\nSetting test prices:");

            // Update simulated prices for testing using market data service
            marketDataService.updatePriceForTesting(niftyIndex, new BigDecimal("19500.50"));
            marketDataService.updatePriceForTesting(bankNiftyIndex, new BigDecimal("42750.25"));
            marketDataService.updatePriceForTesting(sensexIndex, new BigDecimal("65200.75"));

            System.out.println("NIFTY 50 (test): 19500.50");
            System.out.println("NIFTY BANK (test): 42750.25");
            System.out.println("SENSEX (test): 65200.75");
            System.out.println("Test market data prices set successfully.");

            // Refresh positions to propagate new prices
            positionWatchlistService.refreshPositions();

            System.out.println("Positions refreshed with new prices.");
        } catch (Exception e) {
            System.err.println("Error updating test prices: " + e.getMessage());
            e.printStackTrace();
        }
    }
}
