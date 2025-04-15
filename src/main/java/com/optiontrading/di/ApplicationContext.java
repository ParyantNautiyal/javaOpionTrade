package com.optiontrading.di;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.optiontrading.events.EventBus;
import com.optiontrading.resources.ResourceManager;
import com.optiontrading.resources.ThreadManager;
import com.optiontrading.resources.CacheManager;
import com.optiontrading.service.api.KiteConnectClient;
import com.optiontrading.service.api.TradingApiClient;
import com.optiontrading.service.auth.AuthService;
import com.optiontrading.service.auth.KiteAuthService;
import com.optiontrading.service.instrument.InstrumentService;
import com.optiontrading.service.market.MarketDataProvider;
import com.optiontrading.service.market.MarketDataService;
import com.optiontrading.service.order.OrderExecutionCoordinator;
import com.optiontrading.service.order.OrderLoggerService;

import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Application context using Google Guice for dependency injection
 */
public class ApplicationContext {
    private static final Logger LOGGER = Logger.getLogger(ApplicationContext.class.getName());

    // Guice injector
    private final Injector injector;

    /**
     * Initialize the application context with Guice
     */
    public ApplicationContext() {
        // Create Guice injector with the app module
        this.injector = Guice.createInjector(new AppModule());
        LOGGER.info("ApplicationContext initialized with Guice");
    }

    /**
     * Get a service from the Guice injector
     */
    public <T> T getService(Class<T> serviceClass) {
        return injector.getInstance(serviceClass);
    }

    /**
     * Start authenticated services
     */
    public void startAuthenticatedServices() {
        // Get market data service and start it
        MarketDataService marketDataService = getService(MarketDataService.class);
        marketDataService.start();

        // Initialize order execution coordinator
        OrderExecutionCoordinator orderExecutionCoordinator = getService(OrderExecutionCoordinator.class);
        // The coordinator initializes itself in its constructor, just make sure it's
        // created
        if (orderExecutionCoordinator != null) {
            LOGGER.info("OrderExecutionCoordinator initialized");
        } else {
            LOGGER.severe("Failed to initialize OrderExecutionCoordinator");
        }

        // Initialize MainOrderPlacedEventHandler to ensure it's created and subscribed
        // to events
        try {
            Object eventHandler = getService(com.optiontrading.service.order.MainOrderPlacedEventHandler.class);
            if (eventHandler != null) {
                LOGGER.info("MainOrderPlacedEventHandler successfully initialized");
            } else {
                LOGGER.severe("Failed to initialize MainOrderPlacedEventHandler");
            }
        } catch (Exception e) {
            LOGGER.severe("Error initializing MainOrderPlacedEventHandler: " + e.getMessage());
            e.printStackTrace();
        }

        LOGGER.info("Started authenticated services");
    }

    /**
     * Ensure user is authenticated before proceeding
     * 
     * @return true if authenticated, false otherwise
     */
    public boolean ensureAuthenticated() {
        AuthService authService = getService(AuthService.class);

        // First check if we have API credentials - if not, prompt for them
        if (!authService.hasCredentials()) {
            LOGGER.warning("API credentials not found. Will prompt for credentials.");
            if (!promptForCredentials(authService)) {
                LOGGER.severe("Failed to get API credentials from user.");
                return false;
            }
        }

        // Now check if we're authenticated
        if (!authService.isAuthenticated()) {
            LOGGER.warning("Access token is invalid. Forcing re-authentication.");
            return reAuthenticateInteractively();
        }

        return true;
    }

    /**
     * Prompt the user to enter API credentials
     * 
     * @return true if credentials were entered successfully
     */
    private boolean promptForCredentials(AuthService authService) {
        try {
            Scanner scanner = new Scanner(System.in);

            System.out.println("\n===== KITE API CREDENTIALS REQUIRED =====");
            System.out.println("No API credentials found. Please enter your Kite API credentials.");

            System.out.print("\nEnter your Kite API Key: ");
            String apiKey = scanner.nextLine().trim();

            System.out.print("Enter your Kite API Secret: ");
            String apiSecret = scanner.nextLine().trim();

            if (apiKey.isEmpty() || apiSecret.isEmpty()) {
                System.out.println("API Key and Secret cannot be empty!");
                return false;
            }

            // Save the credentials
            authService.setApiCredentials(apiKey, apiSecret);
            LOGGER.info("API credentials saved successfully.");

            // If it's a KiteAuthService, explicitly reload credentials to ensure they're
            // loaded in memory
            if (authService instanceof KiteAuthService) {
                ((KiteAuthService) authService).reloadCredentials();
                LOGGER.info("API credentials explicitly reloaded after saving.");
            }

            return true;
        } catch (Exception e) {
            LOGGER.severe("Error while prompting for credentials: " + e.getMessage());
            return false;
        }
    }

    /**
     * Re-authenticate interactively
     * 
     * @return true if authenticated, false otherwise
     */
    private boolean reAuthenticateInteractively() {
        AuthService authService = getService(AuthService.class);

        try {
            // Get authentication URL
            String authUrl = authService.getLoginUrl();
            System.out.println("\n===== KITE AUTHENTICATION REQUIRED =====");
            System.out.println("Please visit the following URL in your browser to authenticate:");
            System.out.println(authUrl);

            // Get request token from user
            System.out.print("\nAfter logging in, you will be redirected to a URL.");
            System.out.print("\nFind the 'request_token' parameter in the URL and enter it here: ");
            String requestToken = new Scanner(System.in).nextLine().trim();

            if (requestToken.isEmpty()) {
                System.out.println("Request token cannot be empty!");
                return false;
            }

            // Generate access token
            authService.generateAccessToken(requestToken);

            System.out.println("Authentication successful!");
            return true;
        } catch (Exception e) {
            LOGGER.severe("Error during interactive re-authentication: " + e.getMessage());
            System.out.println("Authentication failed: " + e.getMessage());
            return false;
        }
    }

    /**
     * Re-authenticate and restart services
     * 
     * @return true if authenticated and services restarted successfully
     */
    public boolean reAuthenticateAndRestartServices() {
        boolean authenticated = ensureAuthenticated();

        if (authenticated) {
            startAuthenticatedServices();
            return true;
        }

        return false;
    }

    /**
     * Shutdown the application context
     */
    public void shutdown() {
        try {
            // Get the market data service
            MarketDataService marketDataService = getService(MarketDataService.class);
            if (marketDataService != null) {
                marketDataService.shutdown();
                LOGGER.info("MarketDataService shutdown complete");
            }

            // Get the order execution coordinator
            OrderExecutionCoordinator coordinator = getService(OrderExecutionCoordinator.class);
            if (coordinator != null) {
                coordinator.shutdown();
                LOGGER.info("OrderExecutionCoordinator shutdown complete");
            }

            // Get the OrderLoggerService and shut it down
            OrderLoggerService orderLoggerService = getService(OrderLoggerService.class);
            if (orderLoggerService != null) {
                orderLoggerService.shutdown();
                LOGGER.info("OrderLoggerService shutdown complete");
            }

            // Get the auth service
            AuthService authService = getService(AuthService.class);
            if (authService != null) {
                // Nothing to shutdown for auth service
            }

            LOGGER.info("ApplicationContext shutdown complete");
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error during ApplicationContext shutdown", e);
        }
    }
}