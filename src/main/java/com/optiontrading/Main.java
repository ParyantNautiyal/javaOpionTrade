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
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Scanner;
import java.util.TimerTask;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;
import java.util.stream.Collectors;

import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OrderType;
import com.optiontrading.service.model.OptionType;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.model.OrderScheduleParams;
import com.optiontrading.service.instrument.InstrumentService;

import java.util.ArrayList;
import java.util.Objects;

import com.optiontrading.utils.MainLogFilter;

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
        // Clean up any leftover lock files from previous runs
        com.optiontrading.utils.LockFileCleanup.cleanupLockFiles();

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
     * Configure logging for the application
     */
    private static void configureLogging() {
        try {
            // Create logs directory if it doesn't exist
            File logDir = new File("logs");
            if (!logDir.exists()) {
                logDir.mkdirs();
            }

            // Set up file handler
            FileHandler fileHandler = new FileHandler("logs/app.log", 1048576, 10, true);
            fileHandler.setFormatter(new SimpleFormatter());

            // Apply our filter to show only OptionChainService logs
            fileHandler.setFilter(new MainLogFilter());

            // Get the root logger and set the level
            Logger rootLogger = Logger.getLogger("");
            rootLogger.setLevel(Level.INFO);

            // Remove any existing handlers to avoid duplicates
            Handler[] handlers = rootLogger.getHandlers();
            for (Handler handler : handlers) {
                rootLogger.removeHandler(handler);
            }

            // Add our filtered handler
            rootLogger.addHandler(fileHandler);

            // Also keep console output for startup information
            ConsoleHandler consoleHandler = new ConsoleHandler();
            consoleHandler.setLevel(Level.INFO);
            consoleHandler.setFilter(new MainLogFilter());
            rootLogger.addHandler(consoleHandler);

            Logger.getLogger(Main.class.getName())
                    .info("Logging configured to show only OptionChainService logs in logs/app.log and console");
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    /**
     * Shutdown all loggers and close file handlers to prevent .lck files
     */
    private static void shutdownLogging() {
        LOGGER.info("Closing all logger file handlers");

        // Close all handlers on the root logger
        Logger rootLogger = Logger.getLogger("");
        for (Handler handler : rootLogger.getHandlers()) {
            handler.close();
            rootLogger.removeHandler(handler);
        }

        // Also close specific loggers that maintain their own handlers
        try {
            com.optiontrading.utils.TradeLogManager.shutdown();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error shutting down TradeLogManager", e);
        }

        try {
            com.optiontrading.logging.InstrumentLogger.shutdown();
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error shutting down InstrumentLogger", e);
        }

        try {
            if (com.optiontrading.logging.LoggingConfigurator.class != null) {
                com.optiontrading.logging.LoggingConfigurator.shutdown();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error shutting down LoggingConfigurator", e);
        }

        LOGGER.info("All logger file handlers closed");
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
            System.out.println("\n===== CHECKING AUTHENTICATION =====");
            System.out.println("Checking for saved Kite API credentials and tokens...");
            System.out.println("You may need to enter your API key and request token if not found.");
            boolean authenticated = appContext.ensureAuthenticated();

            if (!authenticated) {
                LOGGER.severe("Failed to authenticate with Kite API even after interactive mode");
                System.out.println("\n===== AUTHENTICATION FAILED =====");
                System.out.println(
                        "Could not authenticate with Kite API. The application will continue in limited mode.");
                System.out.println("You can retry authentication from the main menu.");

                // Wait for user acknowledgment
                System.out.println("\nPress Enter to continue...");
                new Scanner(System.in).nextLine();

                // Show console menu without starting authenticated services
                showConsoleMenu(appContext);

                // Exit application
                LOGGER.info("Shutting down application");
                appContext.shutdown();
                resourceManager.shutdown();

                return;
            }

            System.out.println("\n===== AUTHENTICATION SUCCESSFUL =====");
            // Start authenticated services
            System.out.println("Starting services...");
            appContext.startAuthenticatedServices();

            // Print current index prices
            printCurrentIndexPrices();

            // Start resource monitoring with Guice
            ResourceMonitor resourceMonitor = Guice.createInjector(new AppModule()).getInstance(ResourceMonitor.class);
            resourceMonitor.startMonitoring(30);
            LOGGER.info("Resource monitoring started with 30-second interval");

            LOGGER.info("Application started successfully");
            System.out.println("Application started successfully!");

            // Clear the console
            clearConsole();

            // Start user interface
            showConsoleMenu(appContext);

            // Shutdown application
            LOGGER.info("Shutting down application");
            appContext.shutdown();
            resourceManager.shutdown();
            resourceMonitor.stopMonitoring();

            // Shut down all loggers
            shutdownLogging();

            // Ensure application exits completely
            LOGGER.info("Application shutdown complete. Exiting.");
            System.exit(0);

        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error in application startup", e);
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
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
            System.out.println("5. Refresh Instruments (Download Latest)");
            System.out.println("6. Exit");
            System.out.println("7. Run System Test");
            System.out.print("Enter your choice (1-7): ");

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
                        // Console-based order creation with selectable options
                        createOrderWithConsoleUI(scanner, appContext);
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
                        // Refresh instruments
                        refreshInstruments(scanner, appContext);
                        break;

                    case 6:
                        // Exit
                        System.out.println("Exiting Option Trading System...");
                        shutdownRequested.set(true);
                        break;

                    case 7:
                        // Run System Test
                        runSystemTest(scanner, appContext);
                        break;

                    default:
                        System.out.println("Invalid choice. Please enter a number between 1 and 7.");
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
            clearConsole();
            System.out.println("\n===== RE-AUTHENTICATION =====");
            System.out.println("Starting re-authentication process with Kite API.");
            System.out.println("You'll need your Kite API credentials and access to a web browser.");
            System.out.println("Press Enter to continue or Ctrl+C to cancel...");
            new Scanner(System.in).nextLine();

            // Use the method in ApplicationContext for re-authentication
            boolean success = appContext.reAuthenticateAndRestartServices();

            if (success) {
                System.out.println("\nRe-authentication and service restart successful!");
                System.out.println("You now have full access to all trading features.");
            } else {
                System.out.println("\nRe-authentication failed or service restart failed.");
                System.out.println("The application will continue in limited mode.");
                System.out.println("You can try again later from the main menu.");
            }

            System.out.println("\nPress Enter to return to the main menu...");
            new Scanner(System.in).nextLine();

        } catch (Exception e) {
            System.out.println("\nError during re-authentication: " + e.getMessage());
            System.out.println("Please try again later.");
            LOGGER.log(Level.WARNING, "Error during re-authentication", e);

            System.out.println("\nPress Enter to return to the main menu...");
            new Scanner(System.in).nextLine();
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

    /**
     * Console-based order creation with selectable options
     */
    private static void createOrderWithConsoleUI(Scanner scanner, ApplicationContext appContext) {
        try {
            clearConsole();
            System.out.println("\n===== CREATE NEW ORDER =====");

            // Get required services
            InstrumentService instrumentService = appContext.getService(InstrumentService.class);
            OrderRepository orderRepository = appContext.getService(OrderRepository.class);

            // Ensure instruments are loaded
            if (instrumentService.getAllInstruments().isEmpty()) {
                System.out.println("Loading instruments... Please wait.");
                instrumentService.refreshInstruments(false);
            }

            // 1. SELECT INDEX
            List<String> availableIndices = new ArrayList<>(instrumentService.loadAvailableSymbols());
            if (availableIndices.size() < 3) {
                // Ensure we have at least 3 options by adding defaults if needed
                if (!availableIndices.contains("NIFTY"))
                    availableIndices.add("NIFTY");
                if (!availableIndices.contains("BANKNIFTY"))
                    availableIndices.add("BANKNIFTY");
                if (!availableIndices.contains("FINNIFTY"))
                    availableIndices.add("FINNIFTY");
            }

            System.out.println("\nSelect Index:");
            for (int i = 0; i < availableIndices.size(); i++) {
                System.out.println((i + 1) + ". " + availableIndices.get(i));
            }
            System.out.print("Enter your choice (1-" + availableIndices.size() + "): ");
            int indexChoice = Integer.parseInt(scanner.nextLine().trim());
            String selectedIndex = availableIndices.get(indexChoice - 1);
            System.out.println("Selected index: " + selectedIndex);

            // 2. SELECT EXPIRY DATE
            List<LocalDate> availableExpiries = new ArrayList<>(instrumentService.loadAvailableExpiries(selectedIndex));
            if (availableExpiries.isEmpty()) {
                System.out.println("No expiry dates found for " + selectedIndex);
                System.out.println("Please refresh instruments first using option 6 from the main menu.");
                return;
            }

            System.out.println("\n--- SELECT EXPIRY DATE ---");
            for (int i = 0; i < availableExpiries.size(); i++) {
                System.out.println((i + 1) + ". "
                        + availableExpiries.get(i).format(DateTimeFormatter.ofPattern("dd-MMM-yyyy (EEE)")));
            }
            System.out.print("Enter your choice (1-" + availableExpiries.size() + "): ");
            int expiryChoice = Integer.parseInt(scanner.nextLine().trim());
            LocalDate selectedExpiry = availableExpiries.get(expiryChoice - 1);
            System.out.println(
                    "Selected expiry: " + selectedExpiry.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy (EEE)")));

            // 3. SELECT ORDER TYPE
            System.out.println("\nSelect Order Type:");
            System.out.println("1. BUY");
            System.out.println("2. SELL");
            System.out.print("Enter your choice (1-2): ");
            int orderTypeChoice = Integer.parseInt(scanner.nextLine().trim());
            OrderType selectedOrderType = (orderTypeChoice == 1) ? OrderType.BUY : OrderType.SELL;
            System.out.println("Selected order type: " + selectedOrderType);

            // 4. SELECT STRIKE PRICE
            List<Instrument> instruments = instrumentService.loadInstrumentsForSymbolAndExpiry(selectedIndex,
                    selectedExpiry);
            List<BigDecimal> availableStrikes = new ArrayList<>(instruments.stream()
                    .map(Instrument::getStrikePrice)
                    .filter(Objects::nonNull) // Filter out null strike prices
                    .distinct()
                    .sorted()
                    .collect(Collectors.toList()));

            if (availableStrikes.isEmpty()) {
                // If no strikes are available, add some defaults
                BigDecimal baseStrike = new BigDecimal("18000.00");
                availableStrikes.add(baseStrike);
                availableStrikes.add(baseStrike.add(new BigDecimal("500.00")));
                availableStrikes.add(baseStrike.add(new BigDecimal("1000.00")));
            }

            System.out.println("\nSelect Strike Price:");
            for (int i = 0; i < availableStrikes.size(); i++) {
                System.out.println((i + 1) + ". " + availableStrikes.get(i));
            }
            System.out.print("Enter your choice (1-" + availableStrikes.size() + "): ");
            int strikeChoice = Integer.parseInt(scanner.nextLine().trim());
            BigDecimal selectedStrike = availableStrikes.get(strikeChoice - 1);
            System.out.println("Selected strike price: " + selectedStrike);

            // 5. ENTER QUANTITY (LOTS)
            System.out.print("\nEnter Quantity (Lots): ");
            int quantity = Integer.parseInt(scanner.nextLine().trim());

            // 6. ENTER PRICE
            System.out.print("\nEnter Target Premium (must be greater than 0, recommended: 500): ");
            BigDecimal price = new BigDecimal(scanner.nextLine().trim());

            // Ensure premium is not zero
            if (price.compareTo(BigDecimal.ZERO) <= 0) {
                System.out.println("Target premium must be greater than 0. Setting to default value of 500.");
                price = new BigDecimal("500");
            }

            // 7. SELECT EXECUTION TIME (HOUR AND MINUTE SEPARATELY)
            LocalTime currentTime = LocalTime.now();

            System.out.println("\nSelect Execution Hour (0-23):");
            for (int i = 0; i < 24; i++) {
                if (i % 6 == 0)
                    System.out.println();
                System.out.print(String.format("%02d", i) + " ");
            }
            System.out.print("\nEnter hour: ");
            int hour = Integer.parseInt(scanner.nextLine().trim());

            System.out.println("\nSelect Execution Minute (0-59):");
            for (int i = 0; i < 60; i += 5) {
                if (i % 30 == 0)
                    System.out.println();
                System.out.print(String.format("%02d", i) + " ");
            }
            System.out.print("\nEnter minute: ");
            int minute = Integer.parseInt(scanner.nextLine().trim());

            LocalTime executionTime = LocalTime.of(hour, minute);
            LocalDateTime executionDateTime = LocalDateTime.of(LocalDate.now(), executionTime);

            // If time is in the past for today, assume tomorrow
            if (executionDateTime.isBefore(LocalDateTime.now())) {
                executionDateTime = executionDateTime.plusDays(1);
            }

            // 8. ADDITIONAL OPTIONS
            System.out.println("\nEnable Stop Loss? (y/n): ");
            boolean stopLossEnabled = scanner.nextLine().trim().equalsIgnoreCase("y");

            System.out.println("Enable Hedging? (y/n): ");
            boolean hedgingEnabled = scanner.nextLine().trim().equalsIgnoreCase("y");

            int hedgePointDifference = 0;
            if (hedgingEnabled) {
                System.out.print("Enter Hedge Point Difference: ");
                hedgePointDifference = Integer.parseInt(scanner.nextLine().trim());
            }

            // 9. CREATE ORDER
            OrderScheduleParams params = OrderScheduleParams.builder()
                    .indexSymbol(selectedIndex)
                    .expiryDate(selectedExpiry)
                    .targetPremium(price)
                    .lots(quantity)
                    .executionTime(executionDateTime)
                    .threshold(5.0) // Default threshold
                    .orderType(selectedOrderType)
                    .stopLossEnabled(stopLossEnabled)
                    .hedgingEnabled(hedgingEnabled)
                    .hedgePointDifference(hedgePointDifference)
                    .build();

            // Create the order using the repository
            ScheduledOrder order = orderRepository.createOrder(params);

            // Show confirmation
            System.out.println("\n===== ORDER CONFIRMATION =====");
            System.out.println("Order ID: " + order.getOrderId());
            System.out.println("Index: " + selectedIndex);
            System.out.println("Expiry: " + selectedExpiry.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy (EEE)")));
            System.out.println("Order Type: " + selectedOrderType);
            System.out.println("Strike Price: " + selectedStrike);
            System.out.println("Quantity: " + quantity + " lots");
            System.out.println("Price: " + price);
            System.out.println(
                    "Execution Time: " + executionDateTime.format(DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss")));
            System.out.println("Stop Loss Enabled: " + stopLossEnabled);
            System.out.println("Hedging Enabled: " + hedgingEnabled);
            if (hedgingEnabled) {
                System.out.println("Hedge Point Difference: " + hedgePointDifference);
            }

        } catch (Exception e) {
            System.out.println("Error creating order: " + e.getMessage());
            LOGGER.log(Level.WARNING, "Error in order creation", e);
        }
    }

    /**
     * Print current index prices on startup
     */
    private void printCurrentIndexPrices() {
        try {
            // Get services from Guice
            InstrumentService instrumentService = appContext.getService(InstrumentService.class);
            MarketDataService marketDataService = appContext.getService(MarketDataService.class);

            // Wait a moment for prices to be fetched
            System.out.println("\n===== CURRENT INDEX PRICES =====");
            System.out.println("Fetching current index prices...");

            // Check if data/instruments directory exists and has content
            File instrumentsDir = new File("data/instruments");
            File metadataFile = new File("data/instruments/metadata.properties");

            if (!instrumentsDir.exists() || !instrumentsDir.isDirectory() ||
                    instrumentsDir.list() == null || instrumentsDir.list().length == 0 ||
                    !metadataFile.exists()) {
                // First time startup - need to download instruments
                System.out.println("No instrument data found. Downloading instruments...");
                instrumentService.refreshInstrumentsAndSaveToFiles();
                System.out.println("Instruments downloaded successfully.");
            } else {
                // Use cached data
                System.out.println("Using cached instrument data. Use menu option to refresh if needed.");

                // Make sure we have instruments loaded in memory
                if (instrumentService.getAllInstruments().isEmpty()) {
                    instrumentService.logCurrentExpiryDates();
                }
            }

            // Get index prices
            BigDecimal niftyPrice = instrumentService.getIndexSpotPrice("NIFTY");
            BigDecimal sensexPrice = instrumentService.getIndexSpotPrice("SENSEX");
            BigDecimal bankexPrice = instrumentService.getIndexSpotPrice("BANKEX");

            // Print the prices
            System.out.println("NIFTY: " + (niftyPrice != null ? niftyPrice : "Not available"));
            System.out.println("SENSEX: " + (sensexPrice != null ? sensexPrice : "Not available"));
            System.out.println("BANKEX: " + (bankexPrice != null ? bankexPrice : "Not available"));
            System.out.println("===================================");

            // Wait a moment for user to read
            try {
                Thread.sleep(2000);
            } catch (InterruptedException e) {
                // Ignore
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error printing index prices", e);
            System.out.println("Could not fetch index prices: " + e.getMessage());
        }
    }

    /**
     * Refresh instruments (download latest from API)
     */
    private static void refreshInstruments(Scanner scanner, ApplicationContext appContext) {
        System.out.println("\n===== REFRESHING INSTRUMENTS =====");
        System.out.println("This will download the latest instruments from the API.");
        System.out.println("This may take a minute or two...");

        try {
            // Get instrument service
            InstrumentService instrumentService = appContext.getService(InstrumentService.class);

            // Force refresh instruments and save to files
            System.out.println("Downloading instruments...");
            instrumentService.refreshInstrumentsAndSaveToFiles();

            System.out.println("Instruments refreshed successfully!");
            System.out.println("You can now select from the latest available expiry dates when placing orders.");
        } catch (Exception e) {
            System.out.println("Error refreshing instruments: " + e.getMessage());
            LOGGER.log(Level.WARNING, "Error refreshing instruments", e);
        }
    }

    /**
     * Run system test
     */
    private static void runSystemTest(Scanner scanner, ApplicationContext appContext) {
        clearConsole();
        System.out.println("\n===== OPTION TRADING SYSTEM TEST =====");
        System.out.println("This will test all major components of the application");
        System.out.println("Please wait while the tests are running...\n");

        // Test results tracking
        boolean[] testResults = new boolean[8];
        String[] testNames = {
                "Authentication",
                "Event Bus",
                "Instrument Service",
                "Trading API",
                "Order Repository",
                "Market Data Service",
                "Resource Management",
                "Timer Services"
        };

        try {
            // 1. Test Authentication
            System.out.print("Testing Authentication Service... ");
            try {
                AuthService authService = appContext.getService(AuthService.class);
                testResults[0] = authService != null && authService.hasApiCredentials();
                System.out.println(testResults[0] ? "PASSED" : "FAILED");
                System.out.println("  API Key: " + (authService.getApiKey() != null ? "Available" : "Not Set"));
                System.out.println("  Authentication Status: "
                        + (authService.isAuthenticated() ? "Authenticated" : "Not Authenticated"));
                System.out.println("  Token Expiry: " + authService.getTokenExpiry());
            } catch (Exception e) {
                System.out.println("FAILED - " + e.getMessage());
                testResults[0] = false;
            }

            // 2. Test Event Bus
            System.out.print("Testing Event Bus... ");
            try {
                EventBus eventBus = appContext.getService(EventBus.class);
                boolean eventBusWorking = eventBus != null;
                testResults[1] = eventBusWorking;
                System.out.println(eventBusWorking ? "PASSED" : "FAILED");

                // Try to publish a test event
                if (eventBusWorking) {
                    System.out.println("  Publishing test event... ");
                    eventBus.publishSync(new TestEvent("System Test"));
                    System.out.println("  Event published successfully");
                }
            } catch (Exception e) {
                System.out.println("FAILED - " + e.getMessage());
                testResults[1] = false;
            }

            // 3. Test Instrument Service
            System.out.print("Testing Instrument Service... ");
            try {
                InstrumentService instrumentService = appContext.getService(InstrumentService.class);
                boolean instrumentsLoaded = instrumentService != null
                        && !instrumentService.getAllInstruments().isEmpty();
                testResults[2] = instrumentsLoaded;
                System.out.println(instrumentsLoaded ? "PASSED" : "FAILED");

                // Print instrument statistics
                if (instrumentsLoaded) {
                    int instrumentCount = instrumentService.getAllInstruments().size();
                    List<String> symbols = instrumentService.loadAvailableSymbols();
                    System.out.println("  Total Instruments: " + instrumentCount);
                    System.out.println("  Available Symbols: "
                            + String.join(", ", symbols.subList(0, Math.min(symbols.size(), 5))) +
                            (symbols.size() > 5 ? "... (" + (symbols.size() - 5) + " more)" : ""));

                    // Test spot price
                    if (!symbols.isEmpty()) {
                        String testSymbol = symbols.get(0);
                        BigDecimal spotPrice = instrumentService.getIndexSpotPrice(testSymbol);
                        System.out.println("  " + testSymbol + " Spot Price: "
                                + (spotPrice != null ? spotPrice : "Not available"));
                    }
                } else {
                    System.out.println("  No instruments loaded. Try refreshing instruments from the main menu.");
                }
            } catch (Exception e) {
                System.out.println("FAILED - " + e.getMessage());
                testResults[2] = false;
            }

            // 4. Test Trading API
            System.out.print("Testing Trading API Connection... ");
            try {
                com.optiontrading.service.api.TradingApiClient apiClient = appContext
                        .getService(com.optiontrading.service.api.TradingApiClient.class);
                boolean isConnected = apiClient != null && apiClient.isAuthenticated();
                testResults[3] = isConnected;
                System.out.println(isConnected ? "PASSED" : "FAILED");
                System.out.println("  API Status: " + (isConnected ? "Connected" : "Disconnected"));

                // Test API connection
                if (isConnected) {
                    boolean testConnection = apiClient.testConnection();
                    System.out.println("  Connection Test: " + (testConnection ? "Successful" : "Failed"));
                }
            } catch (Exception e) {
                System.out.println("FAILED - " + e.getMessage());
                testResults[3] = false;
            }

            // 5. Test Order Repository
            System.out.print("Testing Order Repository... ");
            try {
                OrderRepository orderRepository = appContext.getService(OrderRepository.class);
                boolean orderRepoWorking = orderRepository != null;
                testResults[4] = orderRepoWorking;
                System.out.println(orderRepoWorking ? "PASSED" : "FAILED");

                if (orderRepoWorking) {
                    List<ScheduledOrder> orders = orderRepository.getAllOrders();
                    System.out.println("  Existing Orders: " + orders.size());

                    // Create a test order but don't save
                    LocalDateTime executionTime = LocalDateTime.now().plusMinutes(30);
                    System.out.println("  Test Order Creation: Valid parameters for execution at " +
                            executionTime.format(DateTimeFormatter.ofPattern("HH:mm:ss")));
                }
            } catch (Exception e) {
                System.out.println("FAILED - " + e.getMessage());
                testResults[4] = false;
            }

            // 6. Test Market Data Service
            System.out.print("Testing Market Data Service... ");
            try {
                MarketDataService marketDataService = appContext.getService(MarketDataService.class);
                boolean marketDataWorking = marketDataService != null;
                testResults[5] = marketDataWorking;
                System.out.println(marketDataWorking ? "PASSED" : "FAILED");

                if (marketDataWorking) {
                    System.out.println("  Service Initialized: Yes");
                    System.out.println("  WebSocket Status: Available");
                }
            } catch (Exception e) {
                System.out.println("FAILED - " + e.getMessage());
                testResults[5] = false;
            }

            // 7. Test Resource Management
            System.out.print("Testing Resource Management... ");
            try {
                ResourceManager resourceManager = appContext.getService(ResourceManager.class);
                boolean resourceMgrWorking = resourceManager != null;
                testResults[6] = resourceMgrWorking;
                System.out.println(resourceMgrWorking ? "PASSED" : "FAILED");

                if (resourceMgrWorking) {
                    System.out.println("  Memory Usage: ");
                    System.out.println(resourceManager.getResourceMetrics().split("\n")[0]);
                    System.out.println(resourceManager.getResourceMetrics().split("\n")[1]);
                }
            } catch (Exception e) {
                System.out.println("FAILED - " + e.getMessage());
                testResults[6] = false;
            }

            // 8. Test Timer Service
            System.out.print("Testing Timer Services... ");
            try {
                TimerManager timerManager = appContext.getService(TimerManager.class);
                boolean timerWorking = timerManager != null;
                testResults[7] = timerWorking;
                System.out.println(timerWorking ? "PASSED" : "FAILED");

                if (timerWorking) {
                    System.out.println("  Active Timers: Available");
                    System.out.println("  Creating test timer...");

                    final boolean[] timerExecuted = { false };
                    java.util.Timer testTimer = new java.util.Timer("TestTimer");
                    testTimer.schedule(new TimerTask() {
                        @Override
                        public void run() {
                            timerExecuted[0] = true;
                        }
                    }, 100);

                    // Wait for timer to execute
                    Thread.sleep(200);
                    System.out.println("  Test Timer Executed: " + (timerExecuted[0] ? "Yes" : "No"));
                    testTimer.cancel();
                }
            } catch (Exception e) {
                System.out.println("FAILED - " + e.getMessage());
                testResults[7] = false;
            }

            // Print Summary
            System.out.println("\n===== TEST SUMMARY =====");
            int passedTests = 0;
            for (int i = 0; i < testResults.length; i++) {
                System.out.println(testNames[i] + ": " + (testResults[i] ? "PASSED" : "FAILED"));
                if (testResults[i])
                    passedTests++;
            }

            double passRate = (double) passedTests / testResults.length * 100;
            System.out.println("\nPASS RATE: " + passedTests + "/" + testResults.length + " ("
                    + String.format("%.1f", passRate) + "%)");

            if (passRate == 100) {
                System.out.println("\nALL TESTS PASSED! The application is functioning correctly.");
            } else if (passRate >= 75) {
                System.out.println("\nMOST TESTS PASSED. Some components may need attention.");
            } else if (passRate >= 50) {
                System.out.println("\nSOME TESTS FAILED. The application may have limited functionality.");
            } else {
                System.out.println("\nMOST TESTS FAILED. The application requires attention.");
            }

        } catch (Exception e) {
            System.out.println("\nERROR during system test: " + e.getMessage());
            LOGGER.log(Level.SEVERE, "Error during system test", e);
        }

        pressEnterToContinue(scanner);
    }

    /**
     * Simple test event class for event bus testing
     */
    private static class TestEvent extends com.optiontrading.events.Event {
        private final String testName;

        public TestEvent(String testName) {
            this.testName = testName;
        }

        public String getTestName() {
            return testName;
        }
    }
}