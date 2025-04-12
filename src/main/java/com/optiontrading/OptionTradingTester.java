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

import java.math.BigDecimal;
import java.util.List;
import java.util.Scanner;
import java.util.logging.Logger;

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

    /**
     * Constructor with dependency injection
     */
    @Inject
    public OptionTradingTester(ResourceManager resourceManager,
            AuthService authService,
            MarketDataService marketDataService,
            PositionWatchlistService positionWatchlistService) {
        this.resourceManager = resourceManager;
        this.authService = authService;
        this.marketDataService = marketDataService;
        this.positionWatchlistService = positionWatchlistService;
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
     * Test market data functionality
     */
    private void testMarketData() {
        System.out.println("\n===== TESTING MARKET DATA =====");

        // Example instrument IDs (NIFTY and BANKNIFTY)
        String niftyIndex = "NSE:NIFTY 50";
        String bankNiftyIndex = "NSE:NIFTY BANK";

        try {
            // Update simulated prices for testing using market data service
            marketDataService.updatePriceForTesting(niftyIndex, new BigDecimal("19500.50"));
            marketDataService.updatePriceForTesting(bankNiftyIndex, new BigDecimal("42750.25"));

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
