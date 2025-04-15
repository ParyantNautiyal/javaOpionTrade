package com.optiontrading.ui;

import com.optiontrading.di.ApplicationContext;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.stage.Stage;

/**
 * JavaFX Application for displaying positions window
 */
public class PositionsApplication extends Application {
    private static ApplicationContext appContext;
    private static boolean initialized = false;
    private static PositionsWindow positionsWindow;

    /**
     * Initialize the JavaFX application with application context
     * 
     * @param appContext the application context
     */
    public static void initialize(ApplicationContext appContext) {
        PositionsApplication.appContext = appContext;
        if (!initialized) {
            // Start JavaFX in a separate thread
            new Thread(() -> {
                try {
                    Application.launch(PositionsApplication.class);
                } catch (Exception e) {
                    System.err.println("Failed to start JavaFX application: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();
            initialized = true;
        }
    }

    /**
     * Open the positions window - safe to call from any thread
     */
    public static void showPositionsWindow() {
        if (!initialized) {
            System.err.println("JavaFX application not initialized! Call initialize() first.");
            return;
        }

        Platform.runLater(() -> {
            if (positionsWindow == null) {
                positionsWindow = new PositionsWindow(appContext);
            }
            positionsWindow.show();
        });
    }

    @Override
    public void start(Stage primaryStage) {
        // Initialize JavaFX application
        Platform.setImplicitExit(false); // Don't exit when all windows are closed

        // Show the positions window
        showPositionsWindow();
    }
}