package com.optiontrading.di;

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

import java.util.logging.Logger;
import java.util.Scanner;

/**
 * Application context for dependency injection
 */
public class ApplicationContext {
    private static final Logger LOGGER = Logger.getLogger(ApplicationContext.class.getName());

    // Service registry for dependency management
    private final ServiceRegistry serviceRegistry;

    // Resource manager
    private final ResourceManager resourceManager;

    /**
     * Initialize the application context
     */
    public ApplicationContext() {
        // Get resource manager instance
        this.resourceManager = ResourceManager.getInstance();

        // Create service registry
        this.serviceRegistry = new ServiceRegistry();

        // Initialize services
        initializeServices();

        LOGGER.info("ApplicationContext initialized");
    }

    /**
     * Get a service from the registry
     */
    public <T> T getService(Class<T> serviceClass) {
        return serviceRegistry.getSingleton(serviceClass);
    }

    /**
     * Initialize service dependencies
     */
    private void initializeServices() {
        // Get the thread manager
        ThreadManager threadManager = resourceManager.getThreadManager();

        // Create and register EventBus
        EventBus eventBus = new EventBus(threadManager);
        serviceRegistry.registerSingleton(EventBus.class, eventBus);

        // Create and register AuthService
        AuthService authService = new KiteAuthService(eventBus);
        serviceRegistry.registerSingleton(AuthService.class, authService);

        // Create and register TradingApiClient without starting connections
        try {
            // Ensure we cast to proper implementation type
            KiteAuthService kiteAuthService = (KiteAuthService) authService;
            TradingApiClient tradingApiClient = new KiteConnectClient(kiteAuthService, eventBus);
            serviceRegistry.registerSingleton(TradingApiClient.class, tradingApiClient);

            // Create and register other services but don't start them yet
            CacheManager cacheManager = resourceManager.getCacheManager();
            KiteConnectClient kiteClient = (KiteConnectClient) tradingApiClient;

            // Create InstrumentService with DI but don't load instruments yet
            InstrumentService instrumentService = new InstrumentService(cacheManager, eventBus, kiteClient);
            serviceRegistry.registerSingleton(InstrumentService.class, instrumentService);

            // Create MarketDataService but don't start it yet
            MarketDataService marketDataService = new MarketDataProvider(tradingApiClient, eventBus);
            serviceRegistry.registerSingleton(MarketDataService.class, marketDataService);

            // Initialize and register OrderExecutionCoordinator
            // This is essential for order execution flow to work
            OrderExecutionCoordinator orderExecutionCoordinator = OrderExecutionCoordinator.getInstance();
            serviceRegistry.registerSingleton(OrderExecutionCoordinator.class, orderExecutionCoordinator);
            LOGGER.info("Registered OrderExecutionCoordinator singleton");

        } catch (Exception e) {
            LOGGER.severe("Error initializing services: " + e.getMessage());
            throw new RuntimeException("Failed to initialize services", e);
        }
    }

    /**
     * Ensure that we're authenticated with Kite API
     * 
     * @return true if authenticated, false if not
     */
    public boolean ensureAuthenticated() {
        return ensureAuthenticated(false);
    }

    /**
     * Ensure that we're authenticated with Kite API
     * 
     * @param interactive whether to use interactive mode for re-authentication
     * @return true if authenticated, false if not
     */
    public boolean ensureAuthenticated(boolean interactive) {
        AuthService authService = getService(AuthService.class);
        TradingApiClient tradingApiClient = getService(TradingApiClient.class);

        // Check if we're already authenticated
        if (tradingApiClient.isAuthenticated()) {
            LOGGER.info("Already authenticated with Kite API");
            return true;
        }

        // Check if we have a valid access token
        if (authService.isAccessTokenValid()) {
            LOGGER.info("Authentication token available, verifying API connectivity...");

            try {
                // Test API connectivity
                boolean apiConnected = tradingApiClient.testConnection();
                if (!apiConnected) {
                    LOGGER.warning(
                            "API connectivity test failed despite having valid tokens. Will attempt re-authentication.");

                    // Invalidate the current token since it's not working with the API
                    authService.invalidateTokens();

                    if (interactive) {
                        // In interactive mode, try to re-authenticate
                        return reAuthenticateInteractively();
                    } else {
                        return false;
                    }
                }

                return true;
            } catch (Exception e) {
                LOGGER.log(java.util.logging.Level.SEVERE, "Error during API connectivity test", e);

                if (interactive) {
                    // In interactive mode, try to re-authenticate
                    return reAuthenticateInteractively();
                } else {
                    return false;
                }
            }
        } else if (!authService.isAccessTokenValid()) {
            // Token is invalid but we can't refresh it automatically
            LOGGER.warning("Access token is invalid. Forcing re-authentication.");

            // Invalidate the current tokens to force a new login flow
            authService.invalidateTokens();

            if (interactive) {
                // In interactive mode, try to re-authenticate
                return reAuthenticateInteractively();
            } else {
                // Retry authentication - recursive call but will take the re-login path now
                return ensureAuthenticated(false);
            }
        }

        return tradingApiClient.isAuthenticated();
    }

    /**
     * Re-authenticate interactively with user input
     * 
     * @return true if re-authentication succeeds, false otherwise
     */
    public boolean reAuthenticateInteractively() {
        AuthService authService = getService(AuthService.class);
        TradingApiClient tradingApiClient = getService(TradingApiClient.class);

        try {
            Scanner scanner = new Scanner(System.in);

            System.out.println("\n===== AUTHENTICATION REQUIRED =====");

            // Check if we have API credentials
            if (!authService.hasApiCredentials()) {
                System.out.println("API credentials not found. Please enter your Kite API credentials:");
                System.out.print("API Key: ");
                String apiKey = scanner.nextLine().trim();
                System.out.print("API Secret: ");
                String apiSecret = scanner.nextLine().trim();

                // Set credentials
                authService.setApiCredentials(apiKey, apiSecret);
                System.out.println("API credentials saved.");
            }

            // Generate login URL
            String loginUrl = authService.getLoginUrl();
            System.out.println("\nPlease login using the following URL:");
            System.out.println(loginUrl);
            System.out.println("\nAfter logging in, you will be redirected to a URL containing a request token.");
            System.out.println("Please enter the request token from the URL:");
            String requestToken = scanner.nextLine().trim();

            if (requestToken.isEmpty()) {
                System.out.println("Request token cannot be empty. Authentication failed.");
                return false;
            }

            // Set request token and generate access token
            authService.setRequestToken(requestToken);
            String accessToken = authService.generateAccessToken(requestToken, null);

            if (accessToken != null && !accessToken.isEmpty()) {
                System.out.println("Access token generated successfully!");

                // Test API connectivity with the new token
                boolean apiConnected = tradingApiClient.testConnection();
                if (apiConnected) {
                    System.out.println("API connectivity verified. Authentication successful!");
                    return true;
                } else {
                    System.out.println("API connectivity test failed with the new token. Authentication failed.");
                    return false;
                }
            } else {
                System.out.println("Failed to generate access token. Authentication failed.");
                return false;
            }
        } catch (Exception e) {
            LOGGER.log(java.util.logging.Level.SEVERE, "Error during interactive re-authentication", e);
            System.out.println("Authentication error: " + e.getMessage());
            return false;
        }
    }

    /**
     * Re-authenticate and restart authenticated services
     * This can be called from the main menu
     * 
     * @return true if re-authentication and services restart succeeded
     */
    public boolean reAuthenticateAndRestartServices() {
        boolean authenticated = ensureAuthenticated(true);

        if (authenticated) {
            try {
                // Restart authenticated services
                startAuthenticatedServices();
                return true;
            } catch (Exception e) {
                LOGGER.log(java.util.logging.Level.SEVERE, "Error restarting services after re-authentication", e);
                System.out.println("Error restarting services: " + e.getMessage());
                return false;
            }
        }

        return false;
    }

    /**
     * Start services that require authentication
     * Should be called after ensureAuthenticated returns true
     */
    public void startAuthenticatedServices() {
        TradingApiClient tradingApiClient = getService(TradingApiClient.class);
        InstrumentService instrumentService = getService(InstrumentService.class);
        MarketDataService marketDataService = getService(MarketDataService.class);

        if (!tradingApiClient.isAuthenticated()) {
            LOGGER.severe("Cannot start authenticated services - not authenticated with Kite API");
            throw new IllegalStateException("Authentication required before starting services");
        }

        // Start the market data service
        LOGGER.info("Starting authenticated services...");

        // Load instruments first (they're needed by MarketDataProvider)
        instrumentService.forceRefreshInstruments();

        // Start market data service
        marketDataService.start();

        LOGGER.info("Authenticated services started successfully");
    }

    /**
     * Shutdown the application context
     */
    public void shutdown() {
        LOGGER.info("Shutting down ApplicationContext");

        // Let resource manager handle other shutdowns
    }
}