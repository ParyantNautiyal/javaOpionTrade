package com.optiontrading.ui.panels;

import com.optiontrading.logging.LoggingConfigurator;
import com.optiontrading.logging.LoggingConfigurator.LoggingMode;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TitledPane;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.logging.Logger;

/**
 * Panel for application settings including logging configuration
 */
public class SettingsPanel extends BorderPane {

    private static final Logger LOGGER = Logger.getLogger(SettingsPanel.class.getName());

    private ComboBox<LoggingMode> loggingModeComboBox;
    private Label currentModeLabel;
    private Label logFileLabelInfo;
    private Text logContentText;

    public SettingsPanel() {
        setPadding(new Insets(20));

        // Title
        Text title = new Text("Settings");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        BorderPane.setAlignment(title, Pos.CENTER);
        setTop(title);

        // Main content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20, 0, 0, 0));

        // Logging configuration section
        TitledPane loggingPane = createLoggingSection();
        content.getChildren().add(loggingPane);

        // Log viewer section
        TitledPane logViewerPane = createLogViewerSection();
        content.getChildren().add(logViewerPane);

        setCenter(content);
    }

    private TitledPane createLoggingSection() {
        VBox loggingContent = new VBox(10);
        loggingContent.setPadding(new Insets(10));

        // Current logging mode display
        HBox currentModeBox = new HBox(10);
        currentModeBox.setAlignment(Pos.CENTER_LEFT);
        currentModeBox.getChildren().addAll(
                new Label("Current Logging Mode:"),
                currentModeLabel = new Label(LoggingConfigurator.getCurrentMode().toString()));
        currentModeLabel.setStyle("-fx-font-weight: bold;");

        // Logging mode selector
        GridPane loggingGrid = new GridPane();
        loggingGrid.setHgap(10);
        loggingGrid.setVgap(10);
        loggingGrid.setPadding(new Insets(10, 0, 0, 0));

        Label modeLabel = new Label("Select Logging Mode:");
        loggingModeComboBox = new ComboBox<>();
        loggingModeComboBox.getItems().addAll(LoggingMode.values());
        loggingModeComboBox.setValue(LoggingConfigurator.getCurrentMode());

        Button applyButton = new Button("Apply");
        applyButton.setOnAction(e -> updateLoggingMode());

        loggingGrid.add(modeLabel, 0, 0);
        loggingGrid.add(loggingModeComboBox, 1, 0);
        loggingGrid.add(applyButton, 2, 0);

        // Description of logging modes
        GridPane descriptionGrid = new GridPane();
        descriptionGrid.setHgap(10);
        descriptionGrid.setVgap(5);
        descriptionGrid.setPadding(new Insets(20, 0, 0, 0));

        int row = 0;
        for (LoggingMode mode : LoggingMode.values()) {
            Label modeNameLabel = new Label(mode.toString());
            modeNameLabel.setStyle("-fx-font-weight: bold;");
            Label modeDescLabel = new Label(getDescriptionForMode(mode));

            descriptionGrid.add(modeNameLabel, 0, row);
            descriptionGrid.add(modeDescLabel, 1, row);
            row++;
        }

        // Add components to the content
        loggingContent.getChildren().addAll(
                currentModeBox,
                new Label("Change the logging mode to focus on different aspects of the application:"),
                loggingGrid,
                descriptionGrid);

        TitledPane loggingPane = new TitledPane("Logging Configuration", loggingContent);
        loggingPane.setExpanded(true);

        return loggingPane;
    }

    private TitledPane createLogViewerSection() {
        VBox viewerContent = new VBox(10);
        viewerContent.setPadding(new Insets(10));

        // Log file info
        HBox logFileBox = new HBox(10);
        logFileBox.setAlignment(Pos.CENTER_LEFT);
        logFileBox.getChildren().addAll(
                new Label("Current Log File:"),
                logFileLabelInfo = new Label(getCurrentLogFile()));

        // Log viewer
        logContentText = new Text("Select a logging mode and apply to view logs...");
        logContentText.setWrappingWidth(800);

        ScrollPane scrollPane = new ScrollPane(logContentText);
        scrollPane.setFitToWidth(true);
        scrollPane.setPrefHeight(400);

        // Refresh button
        Button refreshButton = new Button("Refresh Log View");
        refreshButton.setOnAction(e -> refreshLogView());

        viewerContent.getChildren().addAll(
                logFileBox,
                new Label("Log Content:"),
                scrollPane,
                refreshButton);

        TitledPane viewerPane = new TitledPane("Log Viewer", viewerContent);
        viewerPane.setExpanded(true);

        return viewerPane;
    }

    private String getDescriptionForMode(LoggingMode mode) {
        switch (mode) {
            case STARTUP:
                return "Focus on application startup and service initialization";
            case ORDER_SCHEDULER:
                return "Focus on order scheduling and execution";
            case POSITION_MONITOR:
                return "Focus on position monitoring activities";
            case OPTION_CHAIN:
                return "Focus on option chain calculations";
            case MARKET_DATA:
                return "Focus on market data, quotes, and price updates";
            case FULL:
                return "Show all application logs";
            default:
                return "Unknown mode";
        }
    }

    private void updateLoggingMode() {
        LoggingMode selectedMode = loggingModeComboBox.getValue();
        if (selectedMode != null) {
            try {
                LoggingConfigurator.configureLogging(selectedMode);
                currentModeLabel.setText(selectedMode.toString());
                logFileLabelInfo.setText(getCurrentLogFile());

                // Log the change
                LOGGER.info("Logging mode changed to: " + selectedMode);

                // Refresh the log view
                refreshLogView();
            } catch (Exception e) {
                LOGGER.warning("Failed to update logging mode: " + e.getMessage());
            }
        }
    }

    private String getCurrentLogFile() {
        LoggingMode currentMode = LoggingConfigurator.getCurrentMode();
        return "logs/" + currentMode.name().toLowerCase() + ".log";
    }

    private void refreshLogView() {
        try {
            String logFile = getCurrentLogFile();
            File file = new File(logFile);

            if (file.exists()) {
                // Read the last 50 lines or so (to avoid loading huge files)
                String content = readLastLines(file, 50);
                logContentText.setText(content);
            } else {
                logContentText.setText("Log file does not exist: " + logFile);
            }
        } catch (Exception e) {
            logContentText.setText("Error reading log file: " + e.getMessage());
        }
    }

    private String readLastLines(File file, int lines) {
        try {
            java.util.List<String> allLines = Files.readAllLines(Paths.get(file.toURI()));

            // Get the last N lines or all if fewer than N
            int startLine = Math.max(0, allLines.size() - lines);
            java.util.List<String> lastLines = allLines.subList(startLine, allLines.size());

            // Join the lines
            StringBuilder sb = new StringBuilder();
            for (String line : lastLines) {
                sb.append(line).append("\n");
            }

            return sb.toString();
        } catch (Exception e) {
            return "Error reading file: " + e.getMessage();
        }
    }
}