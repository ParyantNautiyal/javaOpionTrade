package com.optiontrading.ui;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.optiontrading.di.AppModule;
import com.optiontrading.di.ApplicationContext;
import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.service.auth.AuthService;
import com.optiontrading.service.auth.KiteAuthService;
import com.optiontrading.service.api.KiteConnectClient;
import com.optiontrading.service.instrument.InstrumentService;
import com.optiontrading.service.order.OrderRepository;
import com.optiontrading.service.position.PositionWatchlistService;
import com.optiontrading.ui.panels.AuthenticationPanel;
import com.optiontrading.ui.panels.InstrumentBrowserPanel;
import com.optiontrading.ui.panels.OrderHistoryPanel;
import com.optiontrading.ui.panels.OrderSchedulerPanel;
import com.optiontrading.ui.panels.PositionMonitorPanel;
import com.optiontrading.ui.panels.SettingsPanel;
import com.optiontrading.utils.TradeLogManager;

import javafx.application.Application;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.Scene;
import javafx.scene.control.Alert;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.control.Tab;
import javafx.scene.control.TabPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.VBox;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.Date;
import java.util.logging.ConsoleHandler;
import java.util.logging.FileHandler;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.logging.SimpleFormatter;

/**
 * JavaFX Testing UI for Option Trading backend
 */
public class OptionTradingTestUI extends Application {
    private static final Logger LOGGER = Logger.getLogger(OptionTradingTestUI.class.getName());

    // Dependency injection
    private Injector injector;
    private ApplicationContext appContext;
    private EventBus eventBus;

    // UI components
    private TabPane mainTabPane;
    private StatusBar statusBar;

    @Override
    public void start(Stage primaryStage) {
        try {
            // Initialize trade logging
            TradeLogManager.initialize();
            TradeLogManager.logTradeEvent("Application UI started - " + new Date());

            // Configure logging for JavaFX application
            configureLogging();

            // Initialize dependency injection
            injector = Guice.createInjector(new AppModule());
            appContext = injector.getInstance(ApplicationContext.class);
            eventBus = injector.getInstance(EventBus.class);

            // Ensure authentication and proper service initialization
            System.out.println("Initializing services...");

            // Ensure authentication is valid
            if (!appContext.ensureAuthenticated()) {
                showError("Authentication Error", "Authentication Failed",
                        "Could not authenticate with trading service. Please check credentials and try again.");
                return;
            }

            // Start authenticated services - this will initialize OrderExecutionCoordinator
            // and MainOrderPlacedEventHandler
            appContext.startAuthenticatedServices();

            // Explicitly initialize MainOrderPlacedEventHandler
            try {
                Object handler = appContext
                        .getService(com.optiontrading.service.order.MainOrderPlacedEventHandler.class);
                if (handler != null) {
                    System.out.println("MainOrderPlacedEventHandler successfully initialized");
                } else {
                    System.out.println("Warning: Failed to initialize MainOrderPlacedEventHandler");
                }
            } catch (Exception e) {
                System.out.println("Warning: Error initializing MainOrderPlacedEventHandler: " + e.getMessage());
            }

            System.out.println("Services initialized successfully");

            // Set up event subscriptions
            setupEventSubscriptions();

            // Create main layout
            BorderPane root = new BorderPane();

            // Create header
            HeaderBar header = new HeaderBar(primaryStage);
            root.setTop(header);

            // Create tab pane for main content
            mainTabPane = new TabPane();
            mainTabPane.setTabClosingPolicy(TabPane.TabClosingPolicy.UNAVAILABLE);
            root.setCenter(mainTabPane);

            // Add tabs for different components
            addAuthenticationTab();
            addInstrumentBrowserTab();
            addOrderManagementTab();
            addOrderHistoryTab();
            addMarketDataTab();
            addPositionMonitorTab();
            addSystemMonitorTab();
            addSettingsTab();

            // Create status bar
            statusBar = new StatusBar(appContext);
            root.setBottom(statusBar);

            // Set up the scene
            Scene scene = new Scene(root, 1200, 800);
            scene.getStylesheets().add(getClass().getResource("/styles/modern-style.css").toExternalForm());

            primaryStage.setScene(scene);
            primaryStage.setTitle("Option Trading System - Testing UI");
            primaryStage.setOnCloseRequest(e -> shutdown());
            primaryStage.show();

        } catch (Exception e) {
            e.printStackTrace();
            showError("Application Error", "Failed to initialize application", e.getMessage());
        }
    }

    private void setupEventSubscriptions() {
        // Subscribe to authentication events
        eventBus.subscribe(KiteAuthService.AccessTokenGeneratedEvent.class,
                event -> Platform.runLater(() -> statusBar.updateAuthStatus(true)));

        eventBus.subscribe(KiteAuthService.TokensInvalidatedEvent.class,
                event -> Platform.runLater(() -> statusBar.updateAuthStatus(false)));
    }

    private void addAuthenticationTab() {
        AuthService authService = appContext.getService(AuthService.class);
        AuthenticationPanel authPanel = new AuthenticationPanel(authService, eventBus);

        Tab tab = new Tab("Authentication");
        tab.setContent(authPanel);
        mainTabPane.getTabs().add(tab);
    }

    private void addInstrumentBrowserTab() {
        Tab tab = new Tab("Instruments");
        KiteConnectClient kiteClient = appContext.getService(KiteConnectClient.class);
        InstrumentService instrumentService = appContext.getService(InstrumentService.class);
        PositionWatchlistService positionWatchlistService = appContext.getService(PositionWatchlistService.class);
        InstrumentBrowserPanel instrumentPanel = new InstrumentBrowserPanel(kiteClient, eventBus, instrumentService,
                positionWatchlistService);
        tab.setContent(instrumentPanel);
        mainTabPane.getTabs().add(tab);
    }

    private void addOrderManagementTab() {
        Tab tab = new Tab("Orders");
        KiteConnectClient kiteClient = appContext.getService(KiteConnectClient.class);
        OrderRepository orderRepository = appContext.getService(OrderRepository.class);
        OrderSchedulerPanel orderPanel = new OrderSchedulerPanel(kiteClient, eventBus, orderRepository);
        tab.setContent(orderPanel);
        mainTabPane.getTabs().add(tab);
    }

    private void addOrderHistoryTab() {
        Tab tab = new Tab("Order History");
        OrderRepository orderRepository = appContext.getService(OrderRepository.class);
        OrderHistoryPanel historyPanel = new OrderHistoryPanel(eventBus, orderRepository);
        tab.setContent(historyPanel);
        mainTabPane.getTabs().add(tab);
    }

    private void addMarketDataTab() {
        Tab tab = new Tab("Market Data");
        tab.setContent(new VBox(new javafx.scene.control.Label("Market Data - Coming Soon")));
        mainTabPane.getTabs().add(tab);
    }

    private void addPositionMonitorTab() {
        Tab tab = new Tab("Positions");
        PositionWatchlistService positionService = appContext.getService(PositionWatchlistService.class);
        PositionMonitorPanel positionPanel = new PositionMonitorPanel(positionService, eventBus);
        tab.setContent(positionPanel);
        mainTabPane.getTabs().add(tab);
    }

    private void addSystemMonitorTab() {
        Tab tab = new Tab("System");
        tab.setContent(new VBox(new javafx.scene.control.Label("System Monitor - Coming Soon")));
        mainTabPane.getTabs().add(tab);
    }

    private void addSettingsTab() {
        Tab tab = new Tab("Settings");
        tab.setContent(new SettingsPanel());
        mainTabPane.getTabs().add(tab);
    }

    private void showError(String title, String header, String content) {
        Alert alert = new Alert(AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);
        alert.showAndWait();
    }

    private void shutdown() {
        try {
            TradeLogManager.logTradeEvent("Application UI shutting down - " + new Date());
            TradeLogManager.shutdown();

            if (appContext != null) {
                // First shutdown ApplicationContext services
                appContext.shutdown();

                // Then get ResourceManager to properly shutdown all resources
                try {
                    com.optiontrading.resources.ResourceManager resourceManager = appContext
                            .getService(com.optiontrading.resources.ResourceManager.class);
                    if (resourceManager != null) {
                        LOGGER.info("Shutting down ResourceManager from UI");
                        resourceManager.shutdown();
                        LOGGER.info("ResourceManager shutdown completed");
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.SEVERE, "Error shutting down ResourceManager", e);
                }

                // Shutdown all logging
                try {
                    com.optiontrading.logging.LoggingConfigurator.shutdown();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error shutting down logging system", e);
                }

                // Clean up any lock files that might still exist
                try {
                    com.optiontrading.utils.LockFileCleanup.cleanupLockFiles();
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error cleaning up lock files", e);
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    /**
     * Configure logging to go to a file
     */
    private void configureLogging() {
        try {
            // Use the new LoggingConfigurator to set up startup logging
            com.optiontrading.logging.LoggingConfigurator.configureLogging(
                    com.optiontrading.logging.LoggingConfigurator.LoggingMode.STARTUP);

            // Configure a UI-specific logger as well
            Logger uiLogger = Logger.getLogger("com.optiontrading.ui");
            uiLogger.setLevel(Level.INFO);
            uiLogger.info("UI Logging initialized successfully");

            System.out.println("Logging configured using LoggingConfigurator in STARTUP mode");
        } catch (Exception e) {
            System.err.println("Failed to configure logging: " + e.getMessage());
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(args);
    }
}