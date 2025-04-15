package com.optiontrading.ui;

import com.optiontrading.di.ApplicationContext;
import com.optiontrading.events.EventBus;
import com.optiontrading.service.position.PositionWatchlistService;
import com.optiontrading.ui.controller.PositionsController;

import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.stage.Stage;

import java.io.IOException;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Window for displaying positions
 */
public class PositionsWindow {
    private static final Logger LOGGER = Logger.getLogger(PositionsWindow.class.getName());
    private static final String FXML_PATH = "/com/optiontrading/ui/positions.fxml";

    private final ApplicationContext appContext;
    private Stage stage;
    private PositionsController controller;

    public PositionsWindow(ApplicationContext appContext) {
        this.appContext = appContext;
        initializeWindow();
    }

    private void initializeWindow() {
        try {
            // Create a new stage
            stage = new Stage();
            stage.setTitle("Option Trading - Position Monitor");

            // Load FXML
            FXMLLoader loader = new FXMLLoader(getClass().getResource(FXML_PATH));
            Parent root = loader.load();

            // Get controller
            controller = loader.getController();

            // Initialize services
            PositionWatchlistService positionService = appContext.getService(PositionWatchlistService.class);
            EventBus eventBus = appContext.getService(EventBus.class);

            // Set up the controller with services
            controller.initServices(positionService, eventBus);

            // Set up window closing behavior
            stage.setOnCloseRequest(event -> {
                LOGGER.info("Position monitor window closing");
                // Call shutdown on controller to cleanup resources
                if (controller != null) {
                    controller.shutdown();
                }
            });

            // Create scene and configure stage
            Scene scene = new Scene(root);
            stage.setScene(scene);
            stage.setMinWidth(800);
            stage.setMinHeight(500);

            LOGGER.info("Position monitor window initialized");
        } catch (IOException e) {
            LOGGER.log(Level.SEVERE, "Error initializing position monitor window", e);
        }
    }

    /**
     * Show the window
     */
    public void show() {
        if (stage != null) {
            // Make sure we're on the JavaFX thread
            Platform.runLater(() -> {
                stage.show();
                stage.toFront();
            });
        }
    }

    /**
     * Hide the window
     */
    public void hide() {
        if (stage != null) {
            Platform.runLater(() -> stage.hide());
        }
    }
}