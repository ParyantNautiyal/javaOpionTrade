package com.optiontrading;

import com.optiontrading.events.EventBus;
import com.optiontrading.resources.ResourceManager;
import com.optiontrading.service.api.TradingApiClient;
import com.optiontrading.service.auth.AuthService;
import com.optiontrading.service.auth.KiteAuthService;
import com.optiontrading.service.market.MarketDataService;
import com.optiontrading.service.market.MarketDataSubscriber;

import java.math.BigDecimal;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test class for the MarketDataProvider
 */
public class MarketDataTest {
    private static final Logger LOGGER = Logger.getLogger(MarketDataTest.class.getName());

    // Services used by the test
    private final ResourceManager resourceManager;
    private final AuthService authService;
    private final TradingApiClient tradingApiClient;
    private final MarketDataService marketDataService;

    /**
     * Constructor with dependency injection
     */
    public MarketDataTest(ResourceManager resourceManager, AuthService authService,
            TradingApiClient tradingApiClient, MarketDataService marketDataService) {
        this.resourceManager = resourceManager;
        this.authService = authService;
        this.tradingApiClient = tradingApiClient;
        this.marketDataService = marketDataService;
    }

    /**
     * Main entry point for the test
     */
    public static void main(String[] args) {
        LOGGER.info("Starting MarketDataProvider Test");

        try {
            // Initialize resource manager
            ResourceManager resourceManager = ResourceManager.getInstance();
            LOGGER.info("Resource manager initialized");

            // Create an application context with DI
            com.optiontrading.di.ApplicationContext appContext = new com.optiontrading.di.ApplicationContext();

            // Ensure user is authenticated before proceeding
            if (!appContext.ensureAuthenticated()) {
                LOGGER.severe("Authentication failed. Test will exit.");
                return;
            }

            // Start authenticated services
            appContext.startAuthenticatedServices();

            // Get services from context
            EventBus eventBus = appContext.getService(EventBus.class);
            AuthService authService = appContext.getService(AuthService.class);
            TradingApiClient tradingApiClient = appContext.getService(TradingApiClient.class);
            MarketDataService marketDataService = appContext.getService(MarketDataService.class);

            // Create and run the test
            MarketDataTest test = new MarketDataTest(resourceManager, authService, tradingApiClient, marketDataService);
            test.run();
        } catch (Exception e) {
            LOGGER.severe("Error in MarketDataTest: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Run the test
     */
    public void run() {
        LOGGER.info("Running MarketDataProvider Test");

        // Display authentication status
        if (tradingApiClient.isAuthenticated()) {
            LOGGER.info("Already authenticated with Kite. Using saved access token.");

            // Calculate and display token expiry
            if (authService instanceof KiteAuthService) {
                KiteAuthService kiteAuthService = (KiteAuthService) authService;
                String expiryTimestamp = kiteAuthService.getTokenExpiry();
                if (expiryTimestamp != null) {
                    LOGGER.info("Access token will be valid until: " + expiryTimestamp);
                }
            }
        }

        // Test market data
        testMarketData();

        // Keep the test running until user presses Enter
        System.out.println("\nTest running. Press Enter to stop.");
        try {
            System.in.read();
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error reading from console", e);
        }

        // Cleanup when done
        marketDataService.shutdown();
    }

    /**
     * Test market data subscription and updates
     */
    private void testMarketData() {
        // First test if our connection is valid
        LOGGER.info("Testing API connection...");
        boolean connectionOk = tradingApiClient.testConnection();

        if (!connectionOk) {
            LOGGER.severe(
                    "Connection test failed even after authentication. Something is wrong with the API connection.");
            return;
        } else {
            LOGGER.info("Connection test successful. Proceeding with market data test.");
        }

        // List of instruments to subscribe to
        // For NSE stocks, the format is NSE:SYMBOL (e.g., NSE:RELIANCE)
        List<String> instruments = Arrays.asList(
                "NSE:RELIANCE",
                "NSE:INFY",
                "NSE:TCS",
                "NSE:NIFTY50");

        // Create a simple subscriber that logs price updates
        MarketDataSubscriber subscriber = new MarketDataSubscriber() {
            @Override
            public void onPriceUpdate(String instrumentId, BigDecimal price) {
                LOGGER.info("Real-time price update: " + instrumentId + " = " + price);
            }
        };

        // Subscribe to the instruments
        LOGGER.info("Subscribing to instruments: " + instruments);
        marketDataService.subscribe(instruments, subscriber);
    }
}