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

import java.util.logging.Logger;

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

        } catch (Exception e) {
            LOGGER.severe("Error initializing services: " + e.getMessage());
            throw new RuntimeException("Failed to initialize services", e);
        }
    }

    /**
     * Ensure Kite API authentication is complete before starting services
     * 
     * @return true if authentication was successful
     */
    public boolean ensureAuthenticated() {
        TradingApiClient tradingApiClient = getService(TradingApiClient.class);
        AuthService authService = getService(AuthService.class);

        // First check if the API client says it's authenticated
        if (tradingApiClient.isAuthenticated()) {
            LOGGER.info("Authentication token available, verifying API connectivity...");

            // Verify API connectivity with a test call
            boolean apiConnected = tradingApiClient.testConnection();
            if (apiConnected) {
                LOGGER.info("API connection verified successfully");
                return true;
            } else {
                LOGGER.warning(
                        "API connectivity test failed despite having valid tokens. Will attempt re-authentication.");
                // Continue to re-authentication below
            }
        } else {
            LOGGER.info("Authentication required with Kite API");
        }

        // Check if we need to login
        if (authService.needsLogin()) {
            // Show login URL and wait for request token
            String loginUrl = authService.getLoginUrl();
            System.out.println("\n==================================================");
            System.out.println("Please visit the following URL to login to Kite:");
            System.out.println(loginUrl);
            System.out.println("==================================================\n");

            // Wait for request token input
            System.out.print("Enter the request token after authentication: ");

            try {
                // Use BufferedReader for proper line reading
                java.io.BufferedReader reader = new java.io.BufferedReader(
                        new java.io.InputStreamReader(System.in));
                String requestToken = reader.readLine().trim();

                if (requestToken == null || requestToken.isEmpty()) {
                    LOGGER.severe("Empty request token entered. Authentication failed.");
                    return false;
                }

                // Generate access token
                System.out.println("Authenticating with request token...");
                String accessToken = authService.generateAccessToken(requestToken, null);

                if (accessToken != null && !accessToken.isEmpty()) {
                    System.out.println("Authentication successful!");

                    // Verify API connectivity after authentication
                    boolean apiConnected = tradingApiClient.testConnection();
                    if (!apiConnected) {
                        LOGGER.severe("Authentication succeeded but API connectivity test failed.");
                        return false;
                    }

                    return true;
                } else {
                    System.out.println("Authentication failed. Invalid token.");
                    return false;
                }
            } catch (Exception e) {
                LOGGER.log(java.util.logging.Level.SEVERE, "Error during authentication", e);
                System.out.println("Authentication failed: " + e.getMessage());
                return false;
            }
        } else if (!authService.isAccessTokenValid()) {
            // Token is invalid but we can't refresh it automatically
            LOGGER.warning("Access token is invalid but no re-login required. Forcing re-authentication.");

            // Invalidate the current tokens to force a new login flow
            authService.invalidateTokens();

            // Retry authentication - recursive call but will take the re-login path now
            return ensureAuthenticated();
        }

        return tradingApiClient.isAuthenticated();
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
        instrumentService.refreshInstruments(false);

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