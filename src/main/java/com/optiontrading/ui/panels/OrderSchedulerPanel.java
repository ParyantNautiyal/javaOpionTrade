package com.optiontrading.ui.panels;

import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.service.api.KiteConnectClient;
import com.optiontrading.service.model.OrderScheduleParams;
import com.optiontrading.service.model.OrderStatus;
import com.optiontrading.service.model.OrderType;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.model.OptionPair;
import com.optiontrading.service.order.OrderDeletedEvent;
import com.optiontrading.service.order.OrderRepository;
import com.optiontrading.service.order.OrderStatusChangedEvent;
import com.optiontrading.service.option.BestOptionsUpdatedEvent;

import javafx.application.Platform;
import javafx.beans.property.SimpleObjectProperty;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.*;
import javafx.util.Callback;
import javafx.util.StringConverter;

import java.math.BigDecimal;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Panel for scheduling and managing automated orders
 */
public class OrderSchedulerPanel extends BorderPane {

    private final KiteConnectClient kiteClient;
    private final EventBus eventBus;
    private final OrderRepository orderRepository;

    private TableView<ScheduledOrder> orderTable;
    private ObservableList<ScheduledOrder> orderData = FXCollections.observableArrayList();
    private Label statusLabel;

    // Form fields
    private ComboBox<String> indexSymbolField;
    private DatePicker expiryDatePicker;
    private ComboBox<OrderType> orderTypeField;
    private Spinner<Integer> lotsField;
    private Spinner<Double> thresholdField;
    private TextField targetPremiumField;
    private CheckBox hedgingEnabledCheck;
    private Spinner<Integer> hedgePointDiffField;
    private CheckBox stopLossEnabledCheck;
    private CheckBox moveSlToCostCheck;
    private CheckBox trailingSlCheck;
    private DatePicker executionDatePicker;
    private Spinner<Integer> executionHourField;
    private Spinner<Integer> executionMinuteField;

    // Store best option pairs for each order
    private final Map<String, OptionPair> bestOptionPairs = new HashMap<>();

    // Details pane for selected order
    private VBox detailsPane;
    private Label bestOptionsLabel;
    private Label callOptionLabel;
    private Label putOptionLabel;

    public OrderSchedulerPanel(KiteConnectClient kiteClient, EventBus eventBus, OrderRepository orderRepository) {
        this.kiteClient = kiteClient;
        this.eventBus = eventBus;
        this.orderRepository = orderRepository;

        setPadding(new Insets(20));

        // Title
        Text title = new Text("Order Scheduler");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 20));
        BorderPane.setAlignment(title, Pos.CENTER);
        setTop(title);

        // Main content
        VBox content = new VBox(15);
        content.setPadding(new Insets(20, 0, 0, 0));
        setCenter(content);

        // Order creation form
        content.getChildren().add(createOrderForm());

        // Split pane for orders list and details
        SplitPane splitPane = new SplitPane();

        // Order list
        VBox orderListContainer = new VBox(10);
        orderListContainer.getChildren().add(createOrderTable());
        VBox.setVgrow(orderTable, Priority.ALWAYS);

        // Order details pane
        detailsPane = createDetailsPane();

        // Add to split pane
        splitPane.getItems().addAll(orderListContainer, detailsPane);
        splitPane.setDividerPositions(0.7);
        content.getChildren().add(splitPane);
        VBox.setVgrow(splitPane, Priority.ALWAYS);

        // Status label
        statusLabel = new Label("Ready to schedule orders");
        statusLabel.setPadding(new Insets(10, 0, 0, 0));
        content.getChildren().add(statusLabel);

        // Subscribe to events
        subscribeToEvents();

        // Load initial data
        refreshOrders();

        // Add selection listener to show details
        orderTable.getSelectionModel().selectedItemProperty().addListener((obs, oldSelection, newSelection) -> {
            updateDetailsPane(newSelection);
        });
    }

    private void subscribeToEvents() {
        // Subscribe to order status change events
        eventBus.subscribe(OrderStatusChangedEvent.class, new EventSubscriber<OrderStatusChangedEvent>() {
            @Override
            public void onEvent(OrderStatusChangedEvent event) {
                Platform.runLater(() -> {
                    // Refresh the orders table
                    refreshOrders();
                    statusLabel.setText("Order " + event.getOrderId() +
                            " status changed: " + event.getOldStatus() +
                            " → " + event.getNewStatus());
                });
            }
        });

        // Subscribe to order deleted events
        eventBus.subscribe(OrderDeletedEvent.class, new EventSubscriber<OrderDeletedEvent>() {
            @Override
            public void onEvent(OrderDeletedEvent event) {
                Platform.runLater(() -> {
                    refreshOrders();
                    statusLabel.setText("Order " + event.getOrderId() + " deleted");
                });
            }
        });

        // Subscribe to best options updated events
        eventBus.subscribe(BestOptionsUpdatedEvent.class, new EventSubscriber<BestOptionsUpdatedEvent>() {
            @Override
            public void onEvent(BestOptionsUpdatedEvent event) {
                String orderId = event.getOrderId();
                OptionPair optionPair = event.getOptionPair();

                if (optionPair != null) {
                    Platform.runLater(() -> {
                        // Store the option pair
                        bestOptionPairs.put(orderId, optionPair);

                        // Update details if this is the currently selected order
                        ScheduledOrder selectedOrder = orderTable.getSelectionModel().getSelectedItem();
                        if (selectedOrder != null && selectedOrder.getOrderId().equals(orderId)) {
                            updateDetailsPane(selectedOrder);
                        }

                        statusLabel.setText("Best options updated for order " + orderId);
                    });
                }
            }
        });
    }

    private TitledPane createOrderForm() {
        GridPane grid = new GridPane();
        grid.setVgap(10);
        grid.setHgap(15);
        grid.setPadding(new Insets(20));

        int row = 0;

        // Index Symbol
        grid.add(new Label("Index Symbol:"), 0, row);
        indexSymbolField = new ComboBox<>();
        indexSymbolField.getItems().addAll("NIFTY", "BANKNIFTY", "FINNIFTY", "SENSEX", "BANKEX");
        indexSymbolField.setValue("NIFTY");
        indexSymbolField.setPrefWidth(150);
        grid.add(indexSymbolField, 1, row);

        // Expiry Date
        grid.add(new Label("Expiry Date:"), 2, row);
        expiryDatePicker = new DatePicker(LocalDate.now().plusDays(7));
        expiryDatePicker.setPrefWidth(150);
        grid.add(expiryDatePicker, 3, row);
        row++;

        // Order Type (BUY/SELL)
        grid.add(new Label("Order Type:"), 0, row);
        orderTypeField = new ComboBox<>();
        orderTypeField.getItems().addAll(OrderType.BUY, OrderType.SELL);
        orderTypeField.setValue(OrderType.SELL);
        grid.add(orderTypeField, 1, row);

        // Number of Lots
        grid.add(new Label("Lots:"), 2, row);
        lotsField = new Spinner<>(1, 100, 1, 1);
        lotsField.setEditable(true);
        lotsField.setPrefWidth(150);
        grid.add(lotsField, 3, row);
        row++;

        // Strike Selection Threshold
        grid.add(new Label("Strike Threshold (%):"), 0, row);
        thresholdField = new Spinner<>(0.0, 20.0, 5.0, 1.0);
        thresholdField.setEditable(true);
        grid.add(thresholdField, 1, row);

        // Target Premium
        grid.add(new Label("Target Premium:"), 2, row);
        targetPremiumField = new TextField("500");

        // Add helper text to explain target premium
        Label premiumHelperLabel = new Label("* Combined price of call and put options (non-zero)");
        premiumHelperLabel.setStyle("-fx-font-size: 10px; -fx-text-fill: #707070;");
        premiumHelperLabel.setPadding(new Insets(0, 0, 5, 0));

        VBox premiumBox = new VBox(2);
        premiumBox.getChildren().addAll(targetPremiumField, premiumHelperLabel);
        grid.add(premiumBox, 3, row);
        row++;

        // Hedging
        hedgingEnabledCheck = new CheckBox("Enable Hedging");
        grid.add(hedgingEnabledCheck, 0, row);

        grid.add(new Label("Hedge Point Diff:"), 1, row);
        hedgePointDiffField = new Spinner<>(100, 2000, 500, 100);
        hedgePointDiffField.setEditable(true);
        hedgePointDiffField.disableProperty().bind(hedgingEnabledCheck.selectedProperty().not());
        grid.add(hedgePointDiffField, 2, row);
        row++;

        // Stop-Loss
        stopLossEnabledCheck = new CheckBox("Enable Stop-Loss");
        grid.add(stopLossEnabledCheck, 0, row);

        moveSlToCostCheck = new CheckBox("Move SL to Cost");
        moveSlToCostCheck.disableProperty().bind(stopLossEnabledCheck.selectedProperty().not());
        grid.add(moveSlToCostCheck, 1, row);

        trailingSlCheck = new CheckBox("Trailing SL");
        trailingSlCheck.disableProperty().bind(stopLossEnabledCheck.selectedProperty().not());
        grid.add(trailingSlCheck, 2, row);
        row++;

        // Execution Time
        grid.add(new Label("Execution Date:"), 0, row);
        executionDatePicker = new DatePicker(LocalDate.now());
        grid.add(executionDatePicker, 1, row);

        HBox timeBox = new HBox(5);
        executionHourField = new Spinner<>(0, 23, 9, 1);
        executionHourField.setPrefWidth(70);
        executionHourField.setEditable(true);

        executionMinuteField = new Spinner<>(0, 59, 15, 1);
        executionMinuteField.setPrefWidth(70);
        executionMinuteField.setEditable(true);

        timeBox.getChildren().addAll(new Label("Time:"), executionHourField, new Label(":"), executionMinuteField);
        grid.add(timeBox, 2, row, 2, 1);
        row++;

        // Buttons
        Button scheduleButton = new Button("Schedule Order");
        scheduleButton.getStyleClass().add("primary");
        scheduleButton.setOnAction(e -> scheduleOrder());

        Button clearButton = new Button("Clear Form");
        clearButton.setOnAction(e -> clearForm());

        HBox buttonBox = new HBox(10);
        buttonBox.setAlignment(Pos.CENTER_RIGHT);
        buttonBox.getChildren().addAll(clearButton, scheduleButton);
        grid.add(buttonBox, 0, row, 4, 1);

        TitledPane formPane = new TitledPane("Create Scheduled Order", grid);
        formPane.setCollapsible(true);
        formPane.setExpanded(true);
        return formPane;
    }

    private TableView<ScheduledOrder> createOrderTable() {
        orderTable = new TableView<>();
        orderTable.setPlaceholder(new Label("No scheduled orders. Use the form above to schedule orders."));

        // Create columns
        TableColumn<ScheduledOrder, String> idCol = new TableColumn<>("Order ID");
        idCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getOrderId()));
        idCol.setPrefWidth(80);

        TableColumn<ScheduledOrder, String> symbolCol = new TableColumn<>("Symbol");
        symbolCol.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().getParams().getIndexSymbol()));
        symbolCol.setPrefWidth(80);

        TableColumn<ScheduledOrder, LocalDate> expiryCol = new TableColumn<>("Expiry");
        expiryCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getParams().getExpiryDate()));
        expiryCol.setPrefWidth(100);

        TableColumn<ScheduledOrder, String> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(
                data -> new SimpleStringProperty(data.getValue().getParams().getOrderType().toString()));
        typeCol.setPrefWidth(60);

        TableColumn<ScheduledOrder, Integer> lotsCol = new TableColumn<>("Lots");
        lotsCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getParams().getLots()));
        lotsCol.setPrefWidth(60);

        TableColumn<ScheduledOrder, BigDecimal> premiumCol = new TableColumn<>("Premium");
        premiumCol.setCellValueFactory(
                data -> new SimpleObjectProperty<>(data.getValue().getParams().getTargetPremium()));
        premiumCol.setPrefWidth(80);

        TableColumn<ScheduledOrder, LocalDateTime> execTimeCol = new TableColumn<>("Execution Time");
        execTimeCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getExecutionTime()));
        execTimeCol.setPrefWidth(140);
        execTimeCol.setCellFactory(column -> new TableCell<>() {
            private final DateTimeFormatter formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm");

            @Override
            protected void updateItem(LocalDateTime item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                } else {
                    setText(formatter.format(item));
                }
            }
        });

        TableColumn<ScheduledOrder, OrderStatus> statusCol = new TableColumn<>("Status");
        statusCol.setCellValueFactory(data -> new SimpleObjectProperty<>(data.getValue().getStatus()));
        statusCol.setPrefWidth(100);
        statusCol.setCellFactory(column -> new TableCell<>() {
            @Override
            protected void updateItem(OrderStatus item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setStyle("");
                } else {
                    setText(item.toString());
                    switch (item) {
                        case SCHEDULED:
                            setStyle("-fx-text-fill: #2563eb;"); // Blue
                            break;
                        case PREPARING:
                        case ANALYZING:
                        case MONITORING:
                        case HEDGING:
                            setStyle("-fx-text-fill: #ca8a04;"); // Amber
                            break;
                        case EXECUTING:
                            setStyle("-fx-text-fill: #ea580c;"); // Orange
                            break;
                        case COMPLETED:
                            setStyle("-fx-text-fill: #16a34a;"); // Green
                            break;
                        case CANCELLED:
                        case FAILED:
                            setStyle("-fx-text-fill: #dc2626;"); // Red
                            break;
                        default:
                            setStyle("");
                            break;
                    }
                }
            }
        });

        TableColumn<ScheduledOrder, String> actionsCol = new TableColumn<>("Actions");
        actionsCol.setPrefWidth(120);
        actionsCol.setCellFactory(createActionsCellFactory());

        // Add columns to table
        orderTable.getColumns().addAll(
                idCol, symbolCol, expiryCol, typeCol, lotsCol, premiumCol,
                execTimeCol, statusCol, actionsCol);

        // Set up the data
        orderTable.setItems(orderData);

        return orderTable;
    }

    private Callback<TableColumn<ScheduledOrder, String>, TableCell<ScheduledOrder, String>> createActionsCellFactory() {
        return column -> new TableCell<>() {
            private final Button cancelButton = new Button("Cancel");
            private final Button deleteButton = new Button("Delete");

            {
                cancelButton.setStyle("-fx-padding: 2 5; -fx-font-size: 11px;");
                deleteButton.setStyle("-fx-padding: 2 5; -fx-font-size: 11px;");

                HBox buttons = new HBox(5, cancelButton, deleteButton);
                buttons.setAlignment(Pos.CENTER);

                cancelButton.setOnAction(event -> {
                    ScheduledOrder order = getTableRow().getItem();
                    if (order != null) {
                        cancelOrder(order);
                    }
                });

                deleteButton.setOnAction(event -> {
                    ScheduledOrder order = getTableRow().getItem();
                    if (order != null) {
                        deleteOrder(order);
                    }
                });

                setGraphic(buttons);
            }

            @Override
            protected void updateItem(String item, boolean empty) {
                super.updateItem(item, empty);

                if (empty) {
                    setGraphic(null);
                } else {
                    ScheduledOrder order = getTableRow().getItem();
                    if (order != null) {
                        // Only enable cancel for scheduled orders
                        cancelButton.setDisable(order.getStatus() != OrderStatus.SCHEDULED);

                        // Only allow delete for completed, cancelled or failed orders
                        OrderStatus status = order.getStatus();
                        deleteButton.setDisable(status != OrderStatus.COMPLETED &&
                                status != OrderStatus.CANCELLED &&
                                status != OrderStatus.FAILED);
                    }
                    setGraphic(getGraphic());
                }
            }
        };
    }

    private void refreshOrders() {
        if (!kiteClient.isAuthenticated()) {
            statusLabel.setText("Please authenticate with Kite API first");
            return;
        }

        try {
            orderData.clear();
            orderData.addAll(orderRepository.getAllOrders());
            statusLabel.setText("Loaded " + orderData.size() + " scheduled orders");
        } catch (Exception e) {
            statusLabel.setText("Error loading orders: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void scheduleOrder() {
        if (!kiteClient.isAuthenticated()) {
            showError("Authentication Required", "Please authenticate with Kite API first.");
            return;
        }

        try {
            // Validate form
            if (executionDatePicker.getValue() == null || expiryDatePicker.getValue() == null) {
                showError("Validation Error", "Please select both execution date and expiry date.");
                return;
            }

            BigDecimal targetPremium;
            try {
                targetPremium = new BigDecimal(targetPremiumField.getText());
                if (targetPremium.compareTo(BigDecimal.ZERO) <= 0) {
                    showError("Validation Error", "Target premium must be greater than zero.");
                    return;
                }
            } catch (NumberFormatException e) {
                showError("Validation Error", "Target premium must be a valid number.");
                return;
            }

            // Create execution time
            LocalDateTime executionTime = LocalDateTime.of(
                    executionDatePicker.getValue(),
                    LocalTime.of(executionHourField.getValue(), executionMinuteField.getValue()));

            // Check if execution time is in the past
            if (executionTime.isBefore(LocalDateTime.now())) {
                showError("Validation Error", "Execution time cannot be in the past.");
                return;
            }

            // Build order parameters
            OrderScheduleParams.Builder builder = OrderScheduleParams.builder()
                    .indexSymbol(indexSymbolField.getValue())
                    .expiryDate(expiryDatePicker.getValue())
                    .orderType(orderTypeField.getValue())
                    .lots(lotsField.getValue())
                    .threshold(thresholdField.getValue())
                    .targetPremium(targetPremium)
                    .hedgingEnabled(hedgingEnabledCheck.isSelected())
                    .hedgePointDifference(hedgePointDiffField.getValue())
                    .stopLossEnabled(stopLossEnabledCheck.isSelected())
                    .moveSlToCost(moveSlToCostCheck.isSelected())
                    .trailingSl(trailingSlCheck.isSelected())
                    .executionTime(executionTime);

            // Create the order
            ScheduledOrder order = orderRepository.createOrder(builder.build());

            // Update the UI
            refreshOrders();
            clearForm();

            statusLabel.setText("Order scheduled successfully: " + order.getOrderId());
        } catch (Exception e) {
            showError("Error", "Failed to schedule order: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private void clearForm() {
        indexSymbolField.setValue("NIFTY");
        expiryDatePicker.setValue(LocalDate.now().plusDays(7));
        orderTypeField.setValue(OrderType.SELL);
        lotsField.getValueFactory().setValue(1);
        thresholdField.getValueFactory().setValue(5.0);
        targetPremiumField.setText("500");
        hedgingEnabledCheck.setSelected(false);
        hedgePointDiffField.getValueFactory().setValue(500);
        stopLossEnabledCheck.setSelected(false);
        moveSlToCostCheck.setSelected(false);
        trailingSlCheck.setSelected(false);
        executionDatePicker.setValue(LocalDate.now());
        executionHourField.getValueFactory().setValue(9);
        executionMinuteField.getValueFactory().setValue(15);
    }

    private void cancelOrder(ScheduledOrder order) {
        if (order != null) {
            // Confirm cancellation
            boolean confirm = showConfirmation("Cancel Order",
                    "Are you sure you want to cancel order " + order.getOrderId() + "?",
                    "This will prevent the order from executing.");

            if (confirm) {
                try {
                    orderRepository.updateOrderStatus(order.getOrderId(), OrderStatus.CANCELLED);
                    refreshOrders();
                    statusLabel.setText("Order cancelled: " + order.getOrderId());
                } catch (Exception e) {
                    showError("Error", "Failed to cancel order: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private void deleteOrder(ScheduledOrder order) {
        if (order != null) {
            // Confirm deletion
            boolean confirm = showConfirmation("Delete Order",
                    "Are you sure you want to delete order " + order.getOrderId() + "?",
                    "This will permanently remove this order from your history.");

            if (confirm) {
                try {
                    orderRepository.deleteOrder(order.getOrderId());
                    refreshOrders();
                    statusLabel.setText("Order deleted: " + order.getOrderId());
                } catch (Exception e) {
                    showError("Error", "Failed to delete order: " + e.getMessage());
                    e.printStackTrace();
                }
            }
        }
    }

    private void showError(String title, String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle(title);
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    private boolean showConfirmation(String title, String header, String content) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle(title);
        alert.setHeaderText(header);
        alert.setContentText(content);

        return alert.showAndWait().orElse(ButtonType.CANCEL) == ButtonType.OK;
    }

    private VBox createDetailsPane() {
        VBox details = new VBox(15);
        details.setPadding(new Insets(10));
        details.setStyle("-fx-background-color: #f9f9f9; -fx-border-color: #e0e0e0; -fx-border-width: 1px;");

        // Title
        Label title = new Label("Order Details");
        title.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));

        // Add clear separator and highlight the best options section
        Separator separator1 = new Separator();
        separator1.setPadding(new Insets(5, 0, 5, 0));

        Label bestOptionsHeader = new Label("BEST OPTIONS");
        bestOptionsHeader.setFont(Font.font("Segoe UI", FontWeight.BOLD, 14));
        bestOptionsHeader.setTextFill(Color.web("#2563eb"));
        bestOptionsHeader.setPadding(new Insets(5, 0, 5, 0));

        // Best options section
        bestOptionsLabel = new Label("No order selected");
        bestOptionsLabel.setFont(Font.font("Segoe UI", FontWeight.MEDIUM, 14));

        // Call option details
        callOptionLabel = new Label("Call Option: None");
        callOptionLabel.setWrapText(true);

        // Put option details
        putOptionLabel = new Label("Put Option: None");
        putOptionLabel.setWrapText(true);

        details.getChildren().addAll(
                title,
                separator1,
                bestOptionsHeader,
                bestOptionsLabel,
                callOptionLabel,
                putOptionLabel);

        return details;
    }

    private void updateDetailsPane(ScheduledOrder order) {
        if (order == null) {
            bestOptionsLabel.setText("No order selected");
            callOptionLabel.setText("Call Option: None");
            putOptionLabel.setText("Put Option: None");
            return;
        }

        String orderId = order.getOrderId();
        OptionPair optionPair = bestOptionPairs.get(orderId);

        bestOptionsLabel.setText("Best Options for Order: " + orderId);

        if (optionPair != null) {
            if (optionPair.hasCallOption()) {
                callOptionLabel.setText(String.format("Call Option: %s\nStrike: %s\nPrice: %s",
                        optionPair.getCallOption().getTradingSymbol(),
                        optionPair.getCallOption().getStrikePrice(),
                        optionPair.getCallPrice()));
            } else {
                callOptionLabel.setText("Call Option: Not selected yet");
            }

            if (optionPair.hasPutOption()) {
                putOptionLabel.setText(String.format("Put Option: %s\nStrike: %s\nPrice: %s",
                        optionPair.getPutOption().getTradingSymbol(),
                        optionPair.getPutOption().getStrikePrice(),
                        optionPair.getPutPrice()));
            } else {
                putOptionLabel.setText("Put Option: Not selected yet");
            }
        } else {
            callOptionLabel.setText("Call Option: Not selected yet");
            putOptionLabel.setText("Put Option: Not selected yet");
        }
    }
}