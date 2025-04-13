package com.optiontrading.ui;

import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Stage;

/**
 * Header bar for the application with logo and title
 */
public class HeaderBar extends HBox {

    public HeaderBar(Stage stage) {
        setPadding(new Insets(15, 20, 15, 20));
        setStyle("-fx-background-color: #2563eb; -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.3), 5, 0, 0, 2);");
        setSpacing(20);

        // App logo and title
        Label title = new Label("Option Trading System");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        title.setStyle("-fx-text-fill: white;");

        Label subtitle = new Label("Testing Interface");
        subtitle.setFont(Font.font("Segoe UI", FontWeight.NORMAL, 14));
        subtitle.setStyle("-fx-text-fill: rgba(255, 255, 255, 0.8);");

        // Spacer
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        // Version label
        Label version = new Label("v1.0.0");
        version.setStyle("-fx-text-fill: rgba(255, 255, 255, 0.6);");

        // Window control buttons
        HBox windowControls = createWindowControls(stage);

        getChildren().addAll(title, subtitle, spacer, version, windowControls);
    }

    private HBox createWindowControls(Stage stage) {
        HBox controls = new HBox(5);

        // Minimize button
        Button minimizeBtn = new Button("_");
        minimizeBtn.getStyleClass().add("window-button");
        minimizeBtn.setOnAction(e -> stage.setIconified(true));

        // Maximize/Restore button
        Button maximizeBtn = new Button("□");
        maximizeBtn.getStyleClass().add("window-button");
        maximizeBtn.setOnAction(e -> {
            if (stage.isMaximized()) {
                stage.setMaximized(false);
            } else {
                stage.setMaximized(true);
            }
        });

        // Fullscreen button
        Button fullscreenBtn = new Button("⛶");
        fullscreenBtn.getStyleClass().add("window-button");
        fullscreenBtn.setOnAction(e -> {
            stage.setFullScreen(!stage.isFullScreen());
        });

        // Close button
        Button closeBtn = new Button("×");
        closeBtn.getStyleClass().add("window-button");
        closeBtn.getStyleClass().add("close-button");
        closeBtn.setOnAction(e -> stage.close());

        controls.getChildren().addAll(minimizeBtn, maximizeBtn, fullscreenBtn, closeBtn);
        return controls;
    }
}