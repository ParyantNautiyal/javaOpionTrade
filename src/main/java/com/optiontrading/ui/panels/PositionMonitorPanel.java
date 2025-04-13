package com.optiontrading.ui.panels;

import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.service.model.OrderType;
import com.optiontrading.service.position.PositionAddedEvent;
import com.optiontrading.service.position.PositionOrderExecutedEvent;
import com.optiontrading.service.position.PositionRemovedEvent;
import com.optiontrading.service.position.PositionStatus;
import com.optiontrading.service.position.PositionWatchlistService;
import com.optiontrading.service.position.WatchedPosition;

import javafx.application.Platform;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.TableCell;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableRow;
import javafx.scene.control.TableView;
import javafx.scene.control.Alert.AlertType;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.text.DecimalFormat;
import java.time.format.DateTimeFormatter;
import java.util.Optional;

/**
 * UI Panel for monitoring positions from the PositionWatchlistService
 */
public class PositionMonitorPanel extends BorderPane {
    private final PositionWatchlistService positionService;
    private final EventBus eventBus;

    private final TableView<WatchedPosition> positionTable;
    private final ObservableList<WatchedPosition> positionList;
    private final Label statusLabel;

    private static final DateTimeFormatter DATE_TIME_FORMATTER = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
    private static final DecimalFormat DECIMAL_FORMAT = new DecimalFormat("#,##0.00");

    /**
     * Constructor with required services
     * 
     * @param positionService the position watchlist service
     * @param eventBus        the event bus for subscribing to events
     */
    public PositionMonitorPanel(PositionWatchlistService positionService, EventBus eventBus) {
        this.positionService = positionService;
        this.eventBus = eventBus;

        // Create status label
        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-label");

        // Initialize table model
        positionList = FXCollections.observableArrayList();

        // Create controls
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> refreshPositions());

        HBox toolBar = new HBox(10, new Label("Positions:"), refreshButton);
        toolBar.setPadding(new Insets(10));

        // Create position table
        positionTable = createPositionTable();
        VBox.setVgrow(positionTable, Priority.ALWAYS);

        // Set up the layout
        setTop(toolBar);
        setCenter(positionTable);
        setBottom(statusLabel);
        setPadding(new Insets(10));

        // Subscribe to events
        subscribeToEvents();

        // Initial load
        refreshPositions();
    }

    /**
     * Create the position table with all columns
     */
    private TableView<WatchedPosition> createPositionTable() {
        TableView<WatchedPosition> table = new TableView<>();

        // ID column
        TableColumn<WatchedPosition, String> idColumn = new TableColumn<>("ID");
        idColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getId()));

        // Symbol column
        TableColumn<WatchedPosition, String> symbolColumn = new TableColumn<>("Symbol");
        symbolColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getInstrument().getTradingSymbol()));

        // Type column
        TableColumn<WatchedPosition, String> typeColumn = new TableColumn<>("Type");
        typeColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOrderType().toString()));
        typeColumn.setCellFactory(column -> new TableCell<WatchedPosition, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals(OrderType.BUY.toString())) {
                        setStyle("-fx-text-fill: green;");
                    } else {
                        setStyle("-fx-text-fill: red;");
                    }
                }
            }
        });

        // Quantity column
        TableColumn<WatchedPosition, String> quantityColumn = new TableColumn<>("Qty");
        quantityColumn
                .setCellValueFactory(data -> new SimpleStringProperty(String.valueOf(data.getValue().getQuantity())));

        // Entry Price column
        TableColumn<WatchedPosition, String> entryPriceColumn = new TableColumn<>("Entry Price");
        entryPriceColumn
                .setCellValueFactory(data -> new SimpleStringProperty(formatPrice(data.getValue().getEntryPrice())));

        // Current Stop column
        TableColumn<WatchedPosition, String> stopPriceColumn = new TableColumn<>("Stop Price");
        stopPriceColumn.setCellValueFactory(data -> {
            BigDecimal stopPrice = data.getValue().getCurrentStopPrice();
            return new SimpleStringProperty(stopPrice != null ? formatPrice(stopPrice) : "N/A");
        });

        // Status column
        TableColumn<WatchedPosition, String> statusColumn = new TableColumn<>("Status");
        statusColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getStatus().toString()));
        statusColumn.setCellFactory(column -> new TableCell<WatchedPosition, String>() {
            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item);
                    if (item.equals(PositionStatus.ACTIVE.toString())) {
                        setStyle("-fx-text-fill: green;");
                    } else if (item.equals(PositionStatus.ERROR.toString())) {
                        setStyle("-fx-text-fill: red;");
                    } else if (item.equals(PositionStatus.CLOSED.toString())) {
                        setStyle("-fx-text-fill: blue;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        // Entry Time column
        TableColumn<WatchedPosition, String> entryTimeColumn = new TableColumn<>("Entry Time");
        entryTimeColumn.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getEntryTime().format(DATE_TIME_FORMATTER)));

        // Action column
        TableColumn<WatchedPosition, String> actionColumn = new TableColumn<>("Actions");
        actionColumn.setCellFactory(column -> new TableCell<WatchedPosition, String>() {
            final Button closeButton = new Button("Close");

            {
                closeButton.setOnAction(event -> {
                    WatchedPosition position = getTableView().getItems().get(getIndex());
                    handleClosePosition(position);
                });
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);
                if (empty) {
                    setGraphic(null);
                } else {
                    WatchedPosition position = getTableView().getItems().get(getIndex());
                    closeButton.setDisable(!position.isActive());
                    setGraphic(closeButton);
                }
            }
        });

        // Add columns to table
        table.getColumns().addAll(idColumn, symbolColumn, typeColumn, quantityColumn,
                entryPriceColumn, stopPriceColumn, statusColumn, entryTimeColumn, actionColumn);

        // Set table properties
        table.setItems(positionList);
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY);

        // Add row styling based on status
        table.setRowFactory(tv -> new TableRow<WatchedPosition>() {
            @Override
            protected void updateItem(WatchedPosition position, boolean empty) {
                super.updateItem(position, empty);
                if (position == null || empty) {
                    setStyle("");
                } else {
                    if (position.getStatus() == PositionStatus.ERROR) {
                        setStyle("-fx-background-color: #ffeeee;");
                    } else if (position.getStatus() == PositionStatus.CLOSED) {
                        setStyle("-fx-background-color: #eeeeee;");
                    } else {
                        setStyle("");
                    }
                }
            }
        });

        return table;
    }

    /**
     * Subscribe to position events
     */
    private void subscribeToEvents() {
        // Subscribe to position added events
        eventBus.subscribe(PositionAddedEvent.class, new EventSubscriber<PositionAddedEvent>() {
            @Override
            public void onEvent(PositionAddedEvent event) {
                Platform.runLater(() -> {
                    refreshPositions();
                    statusLabel.setText("Position added: " + event.getPosition().getInstrument().getTradingSymbol());
                });
            }
        });

        // Subscribe to position removed events
        eventBus.subscribe(PositionRemovedEvent.class, new EventSubscriber<PositionRemovedEvent>() {
            @Override
            public void onEvent(PositionRemovedEvent event) {
                Platform.runLater(() -> {
                    refreshPositions();
                    statusLabel.setText("Position removed: " + event.getPosition().getInstrument().getTradingSymbol());
                });
            }
        });

        // Subscribe to position order executed events
        eventBus.subscribe(PositionOrderExecutedEvent.class, new EventSubscriber<PositionOrderExecutedEvent>() {
            @Override
            public void onEvent(PositionOrderExecutedEvent event) {
                Platform.runLater(() -> {
                    refreshPositions();
                    statusLabel.setText("Order executed for position: " +
                            event.getPosition().getInstrument().getTradingSymbol());
                });
            }
        });
    }

    /**
     * Refresh the positions list from the service
     */
    private void refreshPositions() {
        positionList.clear();
        positionList.addAll(positionService.getAllPositions());
        statusLabel.setText("Positions refreshed: " + positionList.size() + " positions");
    }

    /**
     * Handle closing a position
     */
    private void handleClosePosition(WatchedPosition position) {
        Alert alert = new Alert(AlertType.CONFIRMATION);
        alert.setTitle("Close Position");
        alert.setHeaderText("Close Position");
        alert.setContentText("Are you sure you want to close position for " +
                position.getInstrument().getTradingSymbol() + "?");

        Optional<ButtonType> result = alert.showAndWait();
        if (result.isPresent() && result.get() == ButtonType.OK) {
            boolean closed = positionService.closePosition(position.getId(), "Manual close from UI");
            if (closed) {
                statusLabel.setText("Position closed: " + position.getInstrument().getTradingSymbol());
                refreshPositions();
            } else {
                statusLabel.setText("Failed to close position: " + position.getInstrument().getTradingSymbol());

                Alert errorAlert = new Alert(AlertType.ERROR);
                errorAlert.setTitle("Error");
                errorAlert.setHeaderText("Failed to Close Position");
                errorAlert.setContentText("Could not close position. Check logs for details.");
                errorAlert.showAndWait();
            }
        }
    }

    /**
     * Format price for display
     */
    private String formatPrice(BigDecimal price) {
        if (price == null) {
            return "N/A";
        }
        return DECIMAL_FORMAT.format(price);
    }
}