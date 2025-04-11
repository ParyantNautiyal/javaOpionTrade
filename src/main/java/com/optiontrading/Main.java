package com.optiontrading;

import com.optiontrading.di.ApplicationContext;
import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.events.MarketDataEvent;
import com.optiontrading.events.OrderEvent;
import com.optiontrading.resources.ResourceManager;
import com.optiontrading.resources.TimerManager;
import com.optiontrading.service.market.MarketDataService;

import java.util.TimerTask;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Main entry point for the Option Trading application.
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());

    public static void main(String[] args) {
        LOGGER.info("Starting Option Trading Application");

        try {
            // Initialize resource manager
            ResourceManager resourceManager = ResourceManager.getInstance();
            LOGGER.info("Resource manager initialized");

            // Initialize application context
            ApplicationContext appContext = new ApplicationContext();
            LOGGER.info("Application context initialized");

            // Setup event subscribers
            EventBus eventBus = appContext.getService(EventBus.class);
            setupEventSubscribers(eventBus);

            // Ensure Kite API authentication
            boolean authenticated = appContext.ensureAuthenticated();
            if (!authenticated) {
                LOGGER.severe("Failed to authenticate with Kite API, exiting application");
                System.exit(1);
            }

            // Start authenticated services
            appContext.startAuthenticatedServices();

            // Get timer manager for metrics reporting
            TimerManager timerManager = resourceManager.getTimerManager();

            // Print resource metrics periodically
            timerManager.scheduleAtFixedRate("MetricsTimer", false, new TimerTask() {
                @Override
                public void run() {
                    LOGGER.info("Resource metrics: \n" + resourceManager.getResourceMetrics());
                }
            }, 5000, 10000);

            LOGGER.info("Application started successfully");

            // Keep the main thread running
            try {
                // Run the application for 10 minutes (or adjust as needed)
                Thread.sleep(600000);
            } catch (InterruptedException e) {
                LOGGER.log(Level.WARNING, "Main thread interrupted", e);
                Thread.currentThread().interrupt();
            }

            // Shutdown application
            LOGGER.info("Shutting down application");
            appContext.shutdown();
            resourceManager.shutdown();
            LOGGER.info("Application shutdown complete");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Application startup failed", e);
            System.exit(1);
        }
    }

    private static void setupEventSubscribers(EventBus eventBus) {
        // Subscribe to market data events
        eventBus.subscribe(MarketDataEvent.class, new EventSubscriber<MarketDataEvent>() {
            @Override
            public void onEvent(MarketDataEvent event) {
                LOGGER.info("Received market data: " + event.getSymbol() +
                        " Last: " + event.getLastPrice());
            }
        });

        // Subscribe to order events
        eventBus.subscribe(OrderEvent.class, new EventSubscriber<OrderEvent>() {
            @Override
            public void onEvent(OrderEvent event) {
                LOGGER.info("Received order event: " + event.getOrderId() +
                        " Status: " + event.getStatus());
            }
        });
    }
}