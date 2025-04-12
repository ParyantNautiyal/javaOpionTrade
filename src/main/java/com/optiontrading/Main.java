package com.optiontrading;

import com.google.inject.Guice;
import com.google.inject.Inject;
import com.google.inject.Injector;
import com.optiontrading.di.AppModule;
import com.optiontrading.di.ApplicationContext;
import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.events.MarketDataEvent;
import com.optiontrading.events.OrderEvent;
import com.optiontrading.resources.ResourceManager;
import com.optiontrading.resources.ResourceMonitor;
import com.optiontrading.resources.TimerManager;
import com.optiontrading.service.auth.AuthService;
import com.optiontrading.service.market.MarketDataService;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.order.OrderRepository;
import com.optiontrading.ui.OrderEntryUI;

import java.io.File;
import java.io.IOException;
import java.util.List;
import java.util.Scanner;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.FileHandler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * Main entry point for the Option Trading application.
 */
public class Main {
    private static final Logger LOGGER = Logger.getLogger(Main.class.getName());
    private static AtomicBoolean shutdownRequested = new AtomicBoolean(false);

    private final ResourceManager resourceManager;
    private final ApplicationContext appContext;
    private final EventBus eventBus;

    @Inject
    public Main(ResourceManager resourceManager, ApplicationContext appContext, EventBus eventBus) {
        this.resourceManager = resourceManager;
        this.appContext = appContext;
        this.eventBus = eventBus;
    }

    public static void main(String[] args) {
        // Configure logging to file to avoid console interference
        configureLogging();

        LOGGER.info("Starting Option Trading Application");

        try {
            // Create Guice injector with AppModule
            Injector injector = Guice.createInjector(new AppModule());

            // Get Main application instance from Guice
            Main app = injector.getInstance(Main.class);

            // Start the application
            app.start();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unhandled exception in main", e);
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Start the application
     */
    public void start() {
        try {
            LOGGER.info("Resource manager initialized");

            LOGGER.info("Application context initialized");

            // Setup event subscribers
            setupEventSubscribers(eventBus);

            // Ensure Kite API authentication with interactive mode
            boolean authenticated = appContext.ensureAuthenticated();
            if (!authenticated) {
                LOGGER.severe("Failed to authenticate with Kite API even after interactive mode, exiting application");
                System.exit(1);
            }

            // Start authenticated services
            appContext.startAuthenticatedServices();

            // Start resource monitoring with Guice
            ResourceMonitor resourceMonitor = Guice.createInjector(new AppModule()).getInstance(ResourceMonitor.class);
            resourceMonitor.startMonitoring(30);
            LOGGER.info("Resource monitoring started with 30-second interval");

            LOGGER.info("Application started successfully");

            // Clear the console
            clearConsole();

            // Start user interface
            showConsoleMenu(appContext);

            // Shutdown application
            LOGGER.info("Shutting down application");
            appContext.shutdown();
            resourceManager.shutdown();
            resourceMonitor.stopMonitoring();

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in application startup", e);
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Configure logging to go to a file instead of console
     */
    private static void configureLogging() {
        try {
            // Create logs directory if it doesn't exist
            File logsDir = new File("logs");
            if (!logsDir.exists()) {
                logsDir.mkdirs();
            }

            // Configure FileHandler to write to logs/app.log
            FileHandler fileHandler = new FileHandler("logs/app.log", true);
            fileHandler.setFormatter(new SimpleFormatter());

            // Add the file handler to the root logger
            Logger rootLogger = Logger.getLogger("");
            rootLogger.addHandler(fileHandler);

            LOGGER.info("Logging configured to write to logs/app.log");
        } catch (IOException e) {
            System.err.println("Failed to configure logging: " + e.getMessage());
        }
    }

    /**
     * Clear the console screen
     */
    private static void clearConsole() {
        try {
            if (System.getProperty("os.name").contains("Windows")) {
                new ProcessBuilder("cmd", "/c", "cls").inheritIO().start().waitFor();
            } else {
                System.out.print("\033[H\033[2J");
                System.out.flush();
            }
        } catch (Exception e) {
            // If clearing fails, just print some newlines
            System.out.println("\n\n\n\n\n\n\n\n\n\n");
        }
    }

    /**
     * Shows the console menu for user interaction
     */
    private static void showConsoleMenu(ApplicationContext appContext) {
        Scanner scanner = new Scanner(System.in);

        while (!shutdownRequested.get()) {
            System.out.println("\n===== OPTION TRADING SYSTEM =====");
            System.out.println("1. Place New Order");
            System.out.println("2. View Existing Orders");
            System.out.println("3. View Resource Metrics");
            System.out.println("4. Re-authenticate with Kite API");
            System.out.println("5. Exit");
            System.out.print("Enter your choice (1-5): ");

            try {
                String input = scanner.nextLine().trim();

                if (input.isEmpty()) {
                    continue; // Skip if input is empty
                }

                int choice = Integer.parseInt(input);

                // Clear screen before processing command
                clearConsole();

                switch (choice) {
                    case 1:
                        // Show order entry UI
                        OrderEntryUI.showOrderEntryUI();
                        break;

                    case 2:
                        // View existing orders
                        showOrderList(scanner, appContext);
                        break;

                    case 3:
                        // View resource metrics
                        showResourceMetrics(scanner, appContext);
                        break;

                    case 4:
                        // Re-authenticate with Kite API
                        handleReauthentication(appContext);
                        break;

                    case 5:
                        // Exit
                        System.out.println("Exiting Option Trading System...");
                        shutdownRequested.set(true);
                        break;

                    default:
                        System.out.println("Invalid choice. Please enter a number between 1 and 5.");
                }
            } catch (NumberFormatException e) {
                System.out.println("Invalid input. Please enter a number.");
            } catch (Exception e) {
                System.out.println("Error: " + e.getMessage());
                LOGGER.log(Level.WARNING, "Error in console menu", e);
            }

            // Small pause to prevent CPU spinning if there's an error in the loop
            if (!shutdownRequested.get()) {
                try {
                    System.out.println("\nPress Enter to continue...");
                    scanner.nextLine();

                    // Clear screen after pressing enter
                    clearConsole();
                } catch (Exception e) {
                    // Ignore
                }
            }
        }

        scanner.close();
    }

    /**
     * Handle re-authentication with Kite API
     */
    private static void handleReauthentication(ApplicationContext appContext) {
        try {
            System.out.println("\n===== RE-AUTHENTICATION =====");

            // Use the new method in ApplicationContext for re-authentication
            boolean success = appContext.reAuthenticateAndRestartServices();

            if (success) {
                System.out.println("Re-authentication and service restart successful!");
            } else {
                System.out.println("Re-authentication failed or service restart failed.");
            }

        } catch (Exception e) {
            System.out.println("Error during re-authentication: " + e.getMessage());
            LOGGER.log(Level.WARNING, "Error during re-authentication", e);
        }
    }

    /**
     * Show the application metrics
     * 
     * @param scanner The scanner for user input
     */
    private static void showResourceMetrics(Scanner scanner, ApplicationContext appContext) {
        clearConsole();
        System.out.println("\n===== RESOURCE METRICS =====");

        // Get resource manager from the context
        ResourceManager resourceManager = appContext.getService(ResourceManager.class);
        System.out.println(resourceManager.getResourceMetrics());

        pressEnterToContinue(scanner);
    }

    /**
     * Show order list
     */
    private static void showOrderList(Scanner scanner, ApplicationContext appContext) {
        clearConsole();
        System.out.println("\n===== SCHEDULED ORDERS =====");

        // Get order repository from the context
        OrderRepository orderRepository = appContext.getService(OrderRepository.class);
        List<ScheduledOrder> orders = orderRepository.getAllOrders();

        if (orders.isEmpty()) {
            System.out.println("No orders scheduled");
        } else {
            for (ScheduledOrder order : orders) {
                System.out.println(order);
            }
        }

        pressEnterToContinue(scanner);
    }

    /**
     * Helper method to wait for user to press Enter
     */
    private static void pressEnterToContinue(Scanner scanner) {
        System.out.println("\nPress Enter to continue...");
        scanner.nextLine();
    }

    private static void setupEventSubscribers(EventBus eventBus) {
        // Subscribe to market data events - log to file, not console
        eventBus.subscribe(MarketDataEvent.class, new EventSubscriber<MarketDataEvent>() {
            @Override
            public void onEvent(MarketDataEvent event) {
                LOGGER.info("Received market data: " + event.getSymbol() +
                        " Last: " + event.getLastPrice());
            }
        });

        // Subscribe to order events - log to file, not console
        eventBus.subscribe(OrderEvent.class, new EventSubscriber<OrderEvent>() {
            @Override
            public void onEvent(OrderEvent event) {
                LOGGER.info("Received order event: " + event.getOrderId() +
                        " Status: " + event.getStatus());
            }
        });
    }
}