package com.optiontrading.ui;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

import com.optiontrading.di.ApplicationContext;
import com.optiontrading.service.api.TradingApiClient;
import com.optiontrading.service.auth.AuthService;

import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;

/**
 * Status bar showing connection status and system information
 */
public class StatusBar extends HBox {

    private final ApplicationContext appContext;
    private final Label authStatusLabel;
    private final Label apiStatusLabel;
    private final Label timestampLabel;

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ofPattern("HH:mm:ss");

    public StatusBar(ApplicationContext appContext) {
        this.appContext = appContext;

        setPadding(new Insets(8, 20, 8, 20));
        setStyle("-fx-background-color: #1e293b;");
        setSpacing(20);

        // Authentication status
        authStatusLabel = new Label();
        authStatusLabel.setStyle("-fx-text-fill: white;");
        updateAuthStatus(appContext.getService(AuthService.class).isAuthenticated());

        // API status
        apiStatusLabel = new Label();
        apiStatusLabel.setStyle("-fx-text-fill: white;");
        updateApiStatus(appContext.getService(TradingApiClient.class).isAuthenticated());

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Timestamp
        timestampLabel = new Label();
        timestampLabel.setStyle("-fx-text-fill: rgba(255, 255, 255, 0.7);");
        updateTimestamp();

        getChildren().addAll(authStatusLabel, apiStatusLabel, spacer, timestampLabel);

        // Start timer to update timestamp
        Thread updateThread = new Thread(() -> {
            while (true) {
                try {
                    Thread.sleep(1000);
                    Platform.runLater(this::updateTimestamp);
                } catch (InterruptedException e) {
                    break;
                }
            }
        });
        updateThread.setDaemon(true);
        updateThread.start();
    }

    public void updateAuthStatus(boolean authenticated) {
        String status = authenticated ? "Authenticated ✓" : "Not Authenticated ✗";
        String color = authenticated ? "#10b981" : "#ef4444";
        authStatusLabel.setText("Auth: " + status);
        authStatusLabel.setStyle("-fx-text-fill: " + color + ";");
    }

    public void updateApiStatus(boolean connected) {
        String status = connected ? "Connected ✓" : "Disconnected ✗";
        String color = connected ? "#10b981" : "#ef4444";
        apiStatusLabel.setText("API: " + status);
        apiStatusLabel.setStyle("-fx-text-fill: " + color + ";");
    }

    private void updateTimestamp() {
        timestampLabel.setText("Last Update: " + LocalDateTime.now().format(TIME_FORMATTER));
    }
}