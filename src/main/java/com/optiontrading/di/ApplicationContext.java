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

import java.util.Scanner;
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

        LOGGER.info("Started authenticated services");
    }

    /**
     * Ensure user is authenticated before proceeding
     * 
     * @return true if authenticated, false otherwise
     */
    public boolean ensureAuthenticated() {
        AuthService authService = getService(AuthService.class);

        if (!authService.isAuthenticated()) {
            LOGGER.warning("Access token is invalid. Forcing re-authentication.");
            return reAuthenticateInteractively();
        }

        return true;
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
            System.out.println("\nPlease visit the following URL to authenticate:");
            System.out.println(authUrl);

            // Get request token from user
            System.out.print("\nEnter the request token from the redirect URL: ");
            String requestToken = new Scanner(System.in).nextLine().trim();

            // Generate access token
            authService.generateAccessToken(requestToken);

            System.out.println("Authentication successful!");
            return true;
        } catch (Exception e) {
            LOGGER.severe("Error during interactive re-authentication: " + e.getMessage());
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
        LOGGER.info("Shutting down ApplicationContext");
    }
}