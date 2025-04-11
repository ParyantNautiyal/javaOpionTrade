package com.optiontrading;

import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.resources.ResourceManager;
import com.optiontrading.service.api.KiteConnectClient;
import com.optiontrading.service.auth.KiteAuthService;
import com.optiontrading.service.instrument.InstrumentService;
import com.optiontrading.service.market.MarketDataProvider;
import com.optiontrading.service.model.OrderScheduleParams;
import com.optiontrading.service.model.OrderStatus;
import com.optiontrading.service.model.OrderType;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.order.HedgeOrdersPlacedEvent;
import com.optiontrading.service.order.MainOrderPlacedEvent;
import com.optiontrading.service.order.OrderExecutionCoordinator;
import com.optiontrading.service.order.OrderRepository;
import com.optiontrading.service.option.BestOptionsUpdatedEvent;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Test application to demonstrate option trading backend functionality
 */
public class TestApplication {
    private static final Logger LOGGER = Logger.getLogger(TestApplication.class.getName());

    // Services
    private final MarketDataProvider marketDataProvider;
    private final InstrumentService instrumentService;
    private final OrderRepository orderRepository;
    private final OrderExecutionCoordinator executionCoordinator;
    private final EventBus eventBus;
    private final KiteConnectClient kiteClient;
    private final KiteAuthService authService;

    /**
     * Create a new test application
     */
    public TestApplication() {
        // Initialize services
        this.marketDataProvider = MarketDataProvider.getInstance();
        this.instrumentService = InstrumentService.getInstance();
        this.orderRepository = OrderRepository.getInstance();
        this.executionCoordinator = OrderExecutionCoordinator.getInstance();
        this.eventBus = EventBus.getInstance();
        this.kiteClient = KiteConnectClient.getInstance();
        this.authService = KiteAuthService.getInstance();

        // Subscribe to events
        subscribeToEvents();
    }

    /**
     * Subscribe to relevant events
     */
    private void subscribeToEvents() {
        // Subscribe to best options updated events
        eventBus.subscribe(BestOptionsUpdatedEvent.class, new EventSubscriber<BestOptionsUpdatedEvent>() {
            @Override
            public void onEvent(BestOptionsUpdatedEvent event) {
                LOGGER.info("Best options updated for order " + event.getOrderId() + ": " + event.getOptionPair());
            }
        });

        // Subscribe to hedge orders placed events
        eventBus.subscribe(HedgeOrdersPlacedEvent.class, new EventSubscriber<HedgeOrdersPlacedEvent>() {
            @Override
            public void onEvent(HedgeOrdersPlacedEvent event) {
                LOGGER.info("Hedge orders placed for order " + event.getOrderId() + ": " + event.getOptionPair());
            }
        });

        // Subscribe to main order placed events
        eventBus.subscribe(MainOrderPlacedEvent.class, new EventSubscriber<MainOrderPlacedEvent>() {
            @Override
            public void onEvent(MainOrderPlacedEvent event) {
                LOGGER.info("Main order placed for order " + event.getOrderId() + ": " + event.getOptionPair());
            }
        });

        // Subscribe to API credentials updated events
        eventBus.subscribe(KiteAuthService.ApiCredentialsUpdatedEvent.class,
                new EventSubscriber<KiteAuthService.ApiCredentialsUpdatedEvent>() {
                    @Override
                    public void onEvent(KiteAuthService.ApiCredentialsUpdatedEvent event) {
                        System.out.println("Event: API Credentials updated. Restart may be needed.");
                    }
                });

        // Subscribe to AccessTokenGeneratedEvent
        eventBus.subscribe(KiteAuthService.AccessTokenGeneratedEvent.class,
                new EventSubscriber<KiteAuthService.AccessTokenGeneratedEvent>() {
                    @Override
                    public void onEvent(KiteAuthService.AccessTokenGeneratedEvent event) {
                        System.out.println("Event: Access token generated/refreshed.");
                    }
                });

        System.out.println("Subscribed to authentication events.");
    }

    /**
     * Run the test application
     */
    public void run() {
        Scanner scanner = new Scanner(System.in);
        boolean running = true;

        printHelp();

        while (running) {
            System.out.print("\nCommand: ");
            String command = scanner.nextLine().trim();

            try {
                switch (command.toLowerCase()) {
                    case "help":
                        printHelp();
                        break;
                    case "list":
                        listOrders();
                        break;
                    case "create":
                        createOrder(scanner);
                        break;
                    case "status":
                        checkOrderStatus(scanner);
                        break;
                    case "cancel":
                        cancelOrder(scanner);
                        break;
                    case "auth setup":
                        setupApiCredentials(scanner);
                        break;
                    case "auth login":
                        loginToKite(scanner);
                        break;
                    case "auth status":
                        checkAuthStatus();
                        break;
                    case "auth logout":
                        logoutFromKite();
                        break;
                    case "refresh instruments":
                        refreshInstruments();
                        break;
                    case "exit":
                        running = false;
                        break;
                    default:
                        System.out.println("Unknown command. Type 'help' for available commands.");
                        break;
                }
            } catch (Exception e) {
                LOGGER.log(Level.SEVERE, "Error processing command", e);
                System.out.println("Error: " + e.getMessage());
            }
        }

        System.out.println("Shutting down...");
        ResourceManager.getInstance().shutdown();
        scanner.close();
    }

    /**
     * Print help information
     */
    private void printHelp() {
        System.out.println("\n== Option Trading Test Application ==");
        System.out.println("Available commands:");
        System.out.println("  help              - Show this help message");
        System.out.println("  list              - List all scheduled orders");
        System.out.println("  create            - Create a new order");
        System.out.println("  status            - Check the status of an order");
        System.out.println("  cancel            - Cancel an order");
        System.out.println("  auth setup        - Setup Kite API credentials");
        System.out.println("  auth login        - Login to Kite Connect");
        System.out.println("  auth status       - Check authentication status");
        System.out.println("  auth logout       - Logout from Kite Connect");
        System.out.println("  refresh instruments - Refresh instrument data from API");
        System.out.println("  exit              - Exit the application");
    }

    /**
     * Setup API credentials
     */
    private void setupApiCredentials(Scanner scanner) {
        System.out.println("\n== Setup API Credentials ==");
        System.out.print("API Key: ");
        String apiKey = scanner.nextLine().trim();
        System.out.print("API Secret: ");
        String apiSecret = scanner.nextLine().trim();

        if (apiKey.isEmpty() || apiSecret.isEmpty()) {
            System.out.println("API key and secret cannot be empty");
            return;
        }

        // Set API credentials using auth service
        authService.setApiCredentials(apiKey, apiSecret);

        System.out.println("API credentials saved successfully");
    }

    /**
     * Login to Kite Connect
     */
    private void loginToKite(Scanner scanner) {
        System.out.println("\n== Login to Kite Connect ==");

        if (!authService.hasApiCredentials()) {
            System.out.println("API credentials not set. Please use 'auth setup' command first");
            return;
        }

        // Get the login URL from auth service
        String loginUrl = authService.getLoginUrl();
        System.out.println("Please open this URL in your browser to login:");
        System.out.println(loginUrl);

        System.out.println("\nAfter logging in, you will be redirected to a page with a request token.");
        System.out.print("Enter the request token: ");
        String requestToken = scanner.nextLine().trim();

        System.out.print("Enter your Kite user ID: ");
        String userId = scanner.nextLine().trim();

        if (requestToken.isEmpty() || userId.isEmpty()) {
            System.out.println("Request token and user ID cannot be empty");
            return;
        }

        // Authenticate with Kite using the auth service
        try {
            authService.setRequestToken(requestToken);
            String accessToken = authService.generateAccessToken(requestToken, userId);
            System.out.println("Authentication successful!");
            System.out.println("Refreshing instruments...");
            instrumentService.refreshInstruments();
        } catch (Exception e) {
            System.out.println("Authentication failed: " + e.getMessage());
            System.out.println("Please try again.");
        }
    }

    /**
     * Check authentication status
     */
    private void checkAuthStatus() {
        System.out.println("\n== Authentication Status ==");

        if (kiteClient.isAuthenticated()) {
            System.out.println("Authenticated with Kite Connect");
            System.out.println("User ID: " + authService.getUserId());
        } else {
            System.out.println("Not authenticated with Kite Connect");

            if (!authService.hasApiCredentials()) {
                System.out.println("API credentials not set. Please use 'auth setup' command first");
            } else {
                System.out.println("Please use 'auth login' command to login");
            }
        }
    }

    /**
     * Logout from Kite Connect
     */
    private void logoutFromKite() {
        System.out.println("\n== Logout from Kite Connect ==");

        if (!kiteClient.isAuthenticated()) {
            System.out.println("Not authenticated with Kite Connect");
            return;
        }

        // Logout through auth service
        authService.invalidateTokens();
        System.out.println("Logged out successfully");
    }

    /**
     * Refresh instruments from API
     */
    private void refreshInstruments() {
        System.out.println("\n== Refreshing Instruments ==");

        if (!kiteClient.isAuthenticated()) {
            System.out.println("Not authenticated with Kite Connect. Using mock data.");
        }

        instrumentService.refreshInstruments();
        System.out.println("Instruments refreshed successfully");
    }

    /**
     * List all scheduled orders
     */
    private void listOrders() {
        System.out.println("\n== Scheduled Orders ==");

        if (orderRepository.getAllOrders().isEmpty()) {
            System.out.println("No orders scheduled.");
            return;
        }

        for (ScheduledOrder order : orderRepository.getAllOrders()) {
            System.out.println("Order ID: " + order.getOrderId());
            System.out.println("  Index: " + order.getParams().getIndexSymbol());
            System.out.println("  Expiry: " + order.getParams().getExpiryDate());
            System.out.println("  Target Premium: " + order.getParams().getTargetPremium());
            System.out.println("  Execution Time: " + formatDateTime(order.getExecutionTime()));
            System.out.println("  Status: " + order.getStatus());
            System.out.println();
        }
    }

    /**
     * Create a new order
     */
    private void createOrder(Scanner scanner) {
        System.out.println("\n== Create Order ==");

        // Get index symbol
        System.out.print("Index symbol (NIFTY, BANKNIFTY, FINNIFTY): ");
        String indexSymbol = scanner.nextLine().trim().toUpperCase();

        // Get expiry date
        LocalDate expiryDate = getExpiryDateInput(scanner);

        // Get threshold
        double threshold = getDoubleInput(scanner, "Strike threshold percentage (default 5.0): ", 5.0);

        // Get target premium
        BigDecimal targetPremium = getBigDecimalInput(scanner, "Target premium: ");

        // Get lots
        int lots = getIntInput(scanner, "Number of lots (default 1): ", 1);

        // Get order type
        OrderType orderType = getOrderTypeInput(scanner);

        // Get hedging options
        boolean hedgingEnabled = getBooleanInput(scanner, "Enable hedging (y/n, default n): ", false);

        int hedgePointDifference = 0;
        if (hedgingEnabled) {
            hedgePointDifference = getIntInput(scanner, "Hedge point difference: ", 0);
        }

        // Get stop loss options
        boolean stopLossEnabled = getBooleanInput(scanner, "Enable stop loss (y/n, default n): ", false);
        boolean moveSlToCost = false;
        boolean trailingSl = false;

        if (stopLossEnabled) {
            moveSlToCost = getBooleanInput(scanner, "Move SL to cost (y/n, default n): ", false);
            trailingSl = getBooleanInput(scanner, "Enable trailing SL (y/n, default n): ", false);
        }

        // Get execution time
        LocalDateTime executionTime = getExecutionTimeInput(scanner);

        // Create order params
        OrderScheduleParams params = OrderScheduleParams.builder()
                .indexSymbol(indexSymbol)
                .expiryDate(expiryDate)
                .threshold(threshold)
                .targetPremium(targetPremium)
                .lots(lots)
                .orderType(orderType)
                .hedgingEnabled(hedgingEnabled)
                .hedgePointDifference(hedgePointDifference)
                .stopLossEnabled(stopLossEnabled)
                .moveSlToCost(moveSlToCost)
                .trailingSl(trailingSl)
                .executionTime(executionTime)
                .build();

        // Create the order
        ScheduledOrder order = orderRepository.createOrder(params);

        System.out.println("Order created with ID: " + order.getOrderId());
    }

    /**
     * Check the status of an order
     */
    private void checkOrderStatus(Scanner scanner) {
        System.out.println("\n== Check Order Status ==");
        System.out.print("Order ID: ");
        String orderId = scanner.nextLine().trim();

        ScheduledOrder order = orderRepository.getOrder(orderId);
        if (order == null) {
            System.out.println("Order not found.");
            return;
        }

        System.out.println("Order ID: " + order.getOrderId());
        System.out.println("Status: " + order.getStatus());
        System.out.println("Execution Time: " + formatDateTime(order.getExecutionTime()));
        System.out.println("Created At: " + formatDateTime(order.getCreatedAt()));
        System.out.println("Updated At: " + formatDateTime(order.getUpdatedAt()));
    }

    /**
     * Cancel an order
     */
    private void cancelOrder(Scanner scanner) {
        System.out.println("\n== Cancel Order ==");
        System.out.print("Order ID: ");
        String orderId = scanner.nextLine().trim();

        ScheduledOrder order = orderRepository.getOrder(orderId);
        if (order == null) {
            System.out.println("Order not found.");
            return;
        }

        // If the order is already completed or failed, we can't cancel it
        if (order.getStatus() == OrderStatus.COMPLETED || order.getStatus() == OrderStatus.FAILED) {
            System.out.println("Cannot cancel order with status: " + order.getStatus());
            return;
        }

        // Update the status to cancelled
        orderRepository.updateOrderStatus(orderId, OrderStatus.CANCELLED);

        System.out.println("Order cancelled.");
    }

    /**
     * Get expiry date input from the user
     */
    private LocalDate getExpiryDateInput(Scanner scanner) {
        while (true) {
            System.out.print("Expiry date (yyyy-MM-dd): ");
            String input = scanner.nextLine().trim();

            try {
                return LocalDate.parse(input);
            } catch (Exception e) {
                System.out.println("Invalid date format. Please use yyyy-MM-dd.");
            }
        }
    }

    /**
     * Get execution time input from the user
     */
    private LocalDateTime getExecutionTimeInput(Scanner scanner) {
        while (true) {
            System.out.print("Execution time (yyyy-MM-dd HH:mm:ss): ");
            String input = scanner.nextLine().trim();

            try {
                return LocalDateTime.parse(input, DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
            } catch (Exception e) {
                System.out.println("Invalid date/time format. Please use yyyy-MM-dd HH:mm:ss.");
            }
        }
    }

    /**
     * Get order type input from the user
     */
    private OrderType getOrderTypeInput(Scanner scanner) {
        while (true) {
            System.out.print("Order type (BUY/SELL, default BUY): ");
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                return OrderType.BUY;
            }

            try {
                return OrderType.valueOf(input.toUpperCase());
            } catch (Exception e) {
                System.out.println("Invalid order type. Please use BUY or SELL.");
            }
        }
    }

    /**
     * Get integer input from the user
     */
    private int getIntInput(Scanner scanner, String prompt, int defaultValue) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                return defaultValue;
            }

            try {
                return Integer.parseInt(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Please enter an integer.");
            }
        }
    }

    /**
     * Get double input from the user
     */
    private double getDoubleInput(Scanner scanner, String prompt, double defaultValue) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            if (input.isEmpty()) {
                return defaultValue;
            }

            try {
                return Double.parseDouble(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Please enter a decimal number.");
            }
        }
    }

    /**
     * Get BigDecimal input from the user
     */
    private BigDecimal getBigDecimalInput(Scanner scanner, String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();

            try {
                return new BigDecimal(input);
            } catch (NumberFormatException e) {
                System.out.println("Invalid number. Please enter a decimal number.");
            }
        }
    }

    /**
     * Get boolean input from the user
     */
    private boolean getBooleanInput(Scanner scanner, String prompt, boolean defaultValue) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim().toLowerCase();

            if (input.isEmpty()) {
                return defaultValue;
            }

            if (input.equals("y") || input.equals("yes") || input.equals("true")) {
                return true;
            } else if (input.equals("n") || input.equals("no") || input.equals("false")) {
                return false;
            } else {
                System.out.println("Invalid input. Please enter y or n.");
            }
        }
    }

    /**
     * Format a date/time for display
     */
    private String formatDateTime(LocalDateTime dateTime) {
        if (dateTime == null) {
            return "N/A";
        }
        return dateTime.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"));
    }

    /**
     * Main method
     */
    public static void main(String[] args) {
        try {
            new TestApplication().run();
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Unhandled exception in main", e);
            System.err.println("Fatal error: " + e.getMessage());
            e.printStackTrace();
        }
    }
}