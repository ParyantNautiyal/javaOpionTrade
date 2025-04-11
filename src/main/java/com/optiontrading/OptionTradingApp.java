package com.optiontrading;

import com.optiontrading.di.ApplicationContext;
import com.optiontrading.events.EventBus;
import com.optiontrading.resources.ResourceManager;
import com.optiontrading.service.api.TradingApiClient;
import com.optiontrading.service.auth.AuthService;
import com.optiontrading.service.auth.KiteAuthService;
import com.optiontrading.service.instrument.InstrumentService;
import com.optiontrading.service.market.MarketDataService;

import java.util.logging.Logger;

/**
 * Main application entry point
 */
public class OptionTradingApp {
    private static final Logger LOGGER = Logger.getLogger(OptionTradingApp.class.getName());

    /**
     * Main method
     */
    public static void main(String[] args) {
        LOGGER.info("Starting Option Trading Application");

        try {
            // Initialize ResourceManager
            ResourceManager resourceManager = ResourceManager.getInstance();

            // Create application context
            ApplicationContext appContext = new ApplicationContext();

            // Register shutdown hook for clean exit
            Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                LOGGER.info("Shutdown hook triggered - cleaning up resources");
                try {
                    // Get services from context
                    MarketDataService marketDataService = appContext.getService(MarketDataService.class);
                    if (marketDataService != null) {
                        marketDataService.shutdown();
                    }

                    // Shutdown application context
                    appContext.shutdown();
                } catch (Exception e) {
                    LOGGER.severe("Error during shutdown: " + e.getMessage());
                }
                LOGGER.info("Application shutdown complete");
            }));

            // Display welcome banner
            displayWelcomeBanner();

            // Ensure authentication is complete before proceeding
            System.out.println("\nChecking Kite API authentication status...");
            if (!appContext.ensureAuthenticated()) {
                System.out.println("\nAuthentication failed or was cancelled. Application will exit.");
                return;
            }

            System.out.println("\nAuthentication successful! Starting application services...");

            // Start authenticated services
            appContext.startAuthenticatedServices();

            // Get services from the context
            AuthService authService = appContext.getService(AuthService.class);
            TradingApiClient tradingApiClient = appContext.getService(TradingApiClient.class);
            MarketDataService marketDataService = appContext.getService(MarketDataService.class);
            InstrumentService instrumentService = appContext.getService(InstrumentService.class);

            // Display application status
            displayApplicationStatus(authService, tradingApiClient, instrumentService);

            // Launch the main application interface (to be implemented)
            // For now, we'll just keep running until user presses Enter
            System.out.println("\nApplication is ready. Press Enter to exit.");
            System.in.read();

            // Shutdown application when done
            LOGGER.info("Shutting down application...");
            marketDataService.shutdown();
            appContext.shutdown();

        } catch (Exception e) {
            LOGGER.severe("Error starting application: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Display a welcome banner for the application
     */
    private static void displayWelcomeBanner() {
        System.out.println("\n==========================================================");
        System.out.println("                 OPTION TRADING APPLICATION                ");
        System.out.println("==========================================================");
        System.out.println("This application helps you analyze options and place trades");
        System.out.println("using Zerodha's Kite Connect API.");
        System.out.println("==========================================================\n");
    }

    /**
     * Display application status
     */
    private static void displayApplicationStatus(AuthService authService, TradingApiClient tradingApiClient,
            InstrumentService instrumentService) {
        System.out.println("\n==========================================================");
        System.out.println("                 APPLICATION STATUS                       ");
        System.out.println("==========================================================");

        System.out.println("API Connection   : " + (tradingApiClient.isAuthenticated() ? "CONNECTED" : "DISCONNECTED"));

        // Show token expiry information if available
        if (authService instanceof KiteAuthService) {
            KiteAuthService kiteAuthService = (KiteAuthService) authService;
            System.out.println("Access Token     : Valid until " + kiteAuthService.getTokenExpiry());
        }

        // Show instruments count
        System.out.println("Instruments      : " + instrumentService.getAllInstruments().size() + " loaded");

        System.out.println("==========================================================\n");
    }
}