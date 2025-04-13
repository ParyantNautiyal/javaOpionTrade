package com.optiontrading.ui.panels;

import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.service.model.OrderType;
import com.optiontrading.service.order.MainOrderPlacedEvent;
import com.optiontrading.service.order.OrderRepository;
import com.optiontrading.service.trading.OrderPlacedEvent;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.control.cell.PropertyValueFactory;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class OrderHistoryPanel extends BorderPane {

    private final EventBus eventBus;
    private final OrderRepository orderRepository;
    private final ObservableList<OrderHistoryItem> orderHistory = FXCollections.observableArrayList();
    private TableView<OrderHistoryItem> orderTable;
    private Label statusLabel;

    public OrderHistoryPanel(EventBus eventBus, OrderRepository orderRepository) {
        this.eventBus = eventBus;
        this.orderRepository = orderRepository;

        setupUI();
        setupEventSubscriptions();
    }

    private void setupUI() {
        setPadding(new Insets(10));

        // Header
        Label titleLabel = new Label("Order History");
        titleLabel.getStyleClass().add("panel-title");

        // Status label
        statusLabel = new Label("Ready");
        statusLabel.getStyleClass().add("status-normal");

        // Button bar
        Button refreshButton = new Button("Refresh");
        refreshButton.setOnAction(e -> loadOrderHistory());

        Button clearButton = new Button("Clear History");
        clearButton.setOnAction(e -> orderHistory.clear());

        HBox buttonBar = new HBox(10, refreshButton, clearButton);

        VBox headerBox = new VBox(5, titleLabel, statusLabel, buttonBar);
        headerBox.setPadding(new Insets(0, 0, 10, 0));
        setTop(headerBox);

        // Order table
        orderTable = new TableView<>();

        TableColumn<OrderHistoryItem, String> idCol = new TableColumn<>("Order ID");
        idCol.setCellValueFactory(new PropertyValueFactory<>("orderId"));

        TableColumn<OrderHistoryItem, String> instrumentCol = new TableColumn<>("Instrument");
        instrumentCol.setCellValueFactory(new PropertyValueFactory<>("instrumentName"));

        TableColumn<OrderHistoryItem, Integer> quantityCol = new TableColumn<>("Quantity");
        quantityCol.setCellValueFactory(new PropertyValueFactory<>("quantity"));

        TableColumn<OrderHistoryItem, BigDecimal> priceCol = new TableColumn<>("Price");
        priceCol.setCellValueFactory(new PropertyValueFactory<>("price"));

        TableColumn<OrderHistoryItem, OrderType> typeCol = new TableColumn<>("Type");
        typeCol.setCellValueFactory(new PropertyValueFactory<>("orderType"));

        TableColumn<OrderHistoryItem, String> tagCol = new TableColumn<>("Tag");
        tagCol.setCellValueFactory(new PropertyValueFactory<>("tag"));

        TableColumn<OrderHistoryItem, String> timestampCol = new TableColumn<>("Timestamp");
        timestampCol.setCellValueFactory(new PropertyValueFactory<>("timestamp"));

        orderTable.getColumns().addAll(idCol, instrumentCol, quantityCol, priceCol, typeCol, tagCol, timestampCol);
        orderTable.setItems(orderHistory);

        VBox.setVgrow(orderTable, Priority.ALWAYS);
        setCenter(orderTable);
    }

    private void setupEventSubscriptions() {
        // Subscribe to order placed events
        eventBus.subscribe(OrderPlacedEvent.class, new EventSubscriber<OrderPlacedEvent>() {
            @Override
            public void onEvent(OrderPlacedEvent event) {
                Platform.runLater(() -> {
                    orderHistory.add(0, new OrderHistoryItem(
                            event.getOrderId(),
                            event.getInstrument() != null ? event.getInstrument().getTradingSymbol() : "Unknown",
                            event.getQuantity(),
                            event.getPrice(),
                            event.getOrderType(),
                            event.getTag() != null ? event.getTag() : "",
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                    statusLabel.setText("Order added: " + event.getOrderId());
                });
            }
        });

        // Subscribe to main order placed events
        eventBus.subscribe(MainOrderPlacedEvent.class, new EventSubscriber<MainOrderPlacedEvent>() {
            @Override
            public void onEvent(MainOrderPlacedEvent event) {
                Platform.runLater(() -> {
                    // Add call option order
                    orderHistory.add(0, new OrderHistoryItem(
                            event.getOrderId() + "-CALL",
                            event.getOptionPair().getCallOption().getTradingSymbol(),
                            1, // quantity
                            BigDecimal.ZERO, // price not available at this point
                            OrderType.BUY,
                            "MAIN_ORDER",
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

                    // Add put option order
                    orderHistory.add(0, new OrderHistoryItem(
                            event.getOrderId() + "-PUT",
                            event.getOptionPair().getPutOption().getTradingSymbol(),
                            1, // quantity
                            BigDecimal.ZERO, // price not available at this point
                            OrderType.BUY,
                            "MAIN_ORDER",
                            LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));

                    statusLabel.setText("Main order added: " + event.getOrderId());
                });
            }
        });
    }

    private void loadOrderHistory() {
        statusLabel.setText("Loading order history...");
        // This would typically load from a repository
        // For now, we'll just rely on the event subscription
        statusLabel.setText("Order history loaded");
    }

    // Model class for order history items
    public static class OrderHistoryItem {
        private final String orderId;
        private final String instrumentName;
        private final int quantity;
        private final BigDecimal price;
        private final OrderType orderType;
        private final String tag;
        private final String timestamp;

        public OrderHistoryItem(String orderId, String instrumentName, int quantity,
                BigDecimal price, OrderType orderType, String tag, String timestamp) {
            this.orderId = orderId;
            this.instrumentName = instrumentName;
            this.quantity = quantity;
            this.price = price;
            this.orderType = orderType;
            this.tag = tag;
            this.timestamp = timestamp;
        }

        public String getOrderId() {
            return orderId;
        }

        public String getInstrumentName() {
            return instrumentName;
        }

        public int getQuantity() {
            return quantity;
        }

        public BigDecimal getPrice() {
            return price;
        }

        public OrderType getOrderType() {
            return orderType;
        }

        public String getTag() {
            return tag;
        }

        public String getTimestamp() {
            return timestamp;
        }
    }
}