package com.optiontrading;

import com.optiontrading.di.ApplicationContext;
import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.events.MarketDataEvent;
import com.optiontrading.events.OrderEvent;
import com.optiontrading.resources.ResourceManager;
import com.optiontrading.resources.TimerManager;
import com.optiontrading.service.auth.AuthService;
import com.optiontrading.service.market.MarketDataService;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.order.OrderRepository;
import com.optiontrading.ui.OrderEntryUI;
import com.optiontrading.resources.ResourceMonitor;

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

    public static void main(String[] args) {
        // Configure logging to file to avoid console interference
        configureLogging();

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

            // Ensure Kite API authentication with interactive mode
            boolean authenticated = appContext.ensureAuthenticated(true);
            if (!authenticated) {
                LOGGER.severe("Failed to authenticate with Kite API even after interactive mode, exiting application");
                System.exit(1);
            }

            // Start authenticated services
            appContext.startAuthenticatedServices();

            // Start resource monitoring
            ResourceMonitor.getInstance().startMonitoring(30);
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
            ResourceMonitor.getInstance().stopMonitoring();
            LOGGER.info("Application shutdown complete");

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Application startup failed", e);
            System.exit(1);
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
                        displayExistingOrders();
                        break;

                    case 3:
                        // View resource metrics
                        System.out.println("\n----- RESOURCE METRICS -----");
                        System.out.println(ResourceManager.getInstance().getResourceMetrics());
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
     * Display existing orders from the repository
     */
    private static void displayExistingOrders() {
        try {
            OrderRepository repository = OrderRepository.getInstance();
            List<ScheduledOrder> orders = repository.getAllOrders();

            System.out.println("\n----- EXISTING ORDERS -----");
            if (orders.isEmpty()) {
                System.out.println("No orders found.");
            } else {
                System.out.println("Order ID | Index | Expiry | Status | Execution Time");
                System.out.println("--------------------------------------------------");
                for (ScheduledOrder order : orders) {
                    System.out.printf("%-8s | %-5s | %-10s | %-10s | %s%n",
                            order.getOrderId().substring(0, Math.min(8, order.getOrderId().length())),
                            order.getParams().getIndexSymbol(),
                            order.getParams().getExpiryDate(),
                            order.getStatus(),
                            order.getExecutionTime());
                }
                System.out.println("Total orders: " + orders.size());
            }
        } catch (Exception e) {
            System.out.println("Error retrieving orders: " + e.getMessage());
            LOGGER.log(Level.WARNING, "Error retrieving orders", e);
        }
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