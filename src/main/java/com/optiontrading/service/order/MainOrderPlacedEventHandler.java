package com.optiontrading.service.order;

import com.optiontrading.events.EventBus;
import com.optiontrading.events.EventSubscriber;
import com.optiontrading.service.model.Instrument;
import com.optiontrading.service.model.OptionPair;
import com.optiontrading.service.model.OrderStatus;
import com.optiontrading.service.model.OrderType;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.position.PositionSource;
import com.optiontrading.service.position.PositionStatus;
import com.optiontrading.service.position.PositionWatchlistService;
import com.optiontrading.service.position.WatchedPosition;
import com.google.inject.Inject;
import com.google.inject.Singleton;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.logging.Logger;

/**
 * Handler for MainOrderPlacedEvent that creates positions in the
 * PositionWatchlistService
 */
@Singleton
public class MainOrderPlacedEventHandler {
    private static final Logger LOGGER = Logger.getLogger(MainOrderPlacedEventHandler.class.getName());

    private final PositionWatchlistService positionWatchlistService;
    private final OrderRepository orderRepository;

    /**
     * Constructor with dependency injection
     * 
     * @param eventBus                 the event bus to subscribe to
     * @param positionWatchlistService the position watchlist service
     * @param orderRepository          the order repository
     */
    @Inject
    public MainOrderPlacedEventHandler(
            EventBus eventBus,
            PositionWatchlistService positionWatchlistService,
            OrderRepository orderRepository) {
        this.positionWatchlistService = positionWatchlistService;
        this.orderRepository = orderRepository;

        LOGGER.info("**** MainOrderPlacedEventHandler constructor called ****");

        // Subscribe to main order placed events
        LOGGER.info("Subscribing to MainOrderPlacedEvent");
        eventBus.subscribe(MainOrderPlacedEvent.class, new EventSubscriber<MainOrderPlacedEvent>() {
            @Override
            public void onEvent(MainOrderPlacedEvent event) {
                handleMainOrderPlaced(event);
            }
        });

        LOGGER.info("==== MainOrderPlacedEventHandler FULLY INITIALIZED ====");
    }

    /**
     * Handle a main order placed event
     * 
     * @param event the event
     */
    private void handleMainOrderPlaced(MainOrderPlacedEvent event) {
        LOGGER.info("Handling main order placed event for order " + event.getOrderId());

        String orderId = event.getOrderId();
        OptionPair optionPair = event.getOptionPair();

        if (optionPair == null) {
            LOGGER.warning("Cannot add positions: option pair is null");
            return;
        }

        // Get the original order to retrieve order parameters
        ScheduledOrder order = orderRepository.getOrder(orderId);
        if (order == null) {
            LOGGER.warning("Cannot add positions: order not found with ID " + orderId);
            return;
        }

        // Check if the order is in a valid status to create positions
        OrderStatus status = order.getStatus();
        if (status != OrderStatus.COMPLETED) {
            LOGGER.warning("Not creating positions for order " + orderId +
                    " because status is " + status + ", not COMPLETED");
            return;
        }

        // Get order parameters
        OrderType orderType = order.getParams().getOrderType();
        int quantity = order.getParams().getLots();
        boolean stopLossEnabled = order.getParams().isStopLossEnabled();
        boolean moveToBreakeven = order.getParams().isMoveSlToCost();
        boolean trailingStopLoss = order.getParams().isTrailingSl();

        LOGGER.info("Adding positions for order " + orderId +
                " - Type: " + orderType +
                ", Quantity: " + quantity +
                ", StopLoss: " + stopLossEnabled);

        // Add call option position
        Instrument callOption = optionPair.getCallOption();
        if (callOption != null) {
            BigDecimal entryPrice = optionPair.getCallPrice();

            // If stop loss is enabled, use the dedicated method for stop loss positions
            if (stopLossEnabled) {
                // Default to 5% stop loss - this could come from a configuration
                BigDecimal stopLossPercentage = new BigDecimal("5.0");

                // Create position with stop loss
                WatchedPosition callPosition = positionWatchlistService.addStopLossPosition(
                        callOption,
                        orderType,
                        quantity,
                        entryPrice,
                        stopLossPercentage,
                        moveToBreakeven,
                        trailingStopLoss,
                        new BigDecimal("0.5") // Default trailing distance, could be configurable
                );

                if (callPosition != null) {
                    LOGGER.info("Added call option position with stop loss to watchlist: " + callPosition);
                } else {
                    LOGGER.warning("Failed to add call option position with stop loss to watchlist");
                }
            } else {
                // Create a regular position without stop loss
                WatchedPosition callPosition = WatchedPosition.builder()
                        .instrument(callOption)
                        .orderType(orderType)
                        .quantity(quantity)
                        .entryPrice(entryPrice)
                        .entryTime(LocalDateTime.now())
                        .source(PositionSource.STRATEGY)
                        .status(PositionStatus.ACTIVE)
                        .build();

                // Add to watchlist
                boolean added = positionWatchlistService.addPosition(callPosition);
                if (added) {
                    LOGGER.info("Added call option position to watchlist: " + callPosition);
                } else {
                    LOGGER.warning("Failed to add call option position to watchlist");
                }
            }
        }

        // Add put option position
        Instrument putOption = optionPair.getPutOption();
        if (putOption != null) {
            BigDecimal entryPrice = optionPair.getPutPrice();

            // If stop loss is enabled, use the dedicated method for stop loss positions
            if (stopLossEnabled) {
                // Default to 5% stop loss - this could come from a configuration
                BigDecimal stopLossPercentage = new BigDecimal("5.0");

                // Create position with stop loss
                WatchedPosition putPosition = positionWatchlistService.addStopLossPosition(
                        putOption,
                        orderType,
                        quantity,
                        entryPrice,
                        stopLossPercentage,
                        moveToBreakeven,
                        trailingStopLoss,
                        new BigDecimal("0.5") // Default trailing distance, could be configurable
                );

                if (putPosition != null) {
                    LOGGER.info("Added put option position with stop loss to watchlist: " + putPosition);
                } else {
                    LOGGER.warning("Failed to add put option position with stop loss to watchlist");
                }
            } else {
                // Create a regular position without stop loss
                WatchedPosition putPosition = WatchedPosition.builder()
                        .instrument(putOption)
                        .orderType(orderType)
                        .quantity(quantity)
                        .entryPrice(entryPrice)
                        .entryTime(LocalDateTime.now())
                        .source(PositionSource.STRATEGY)
                        .status(PositionStatus.ACTIVE)
                        .build();

                // Add to watchlist
                boolean added = positionWatchlistService.addPosition(putPosition);
                if (added) {
                    LOGGER.info("Added put option position to watchlist: " + putPosition);
                } else {
                    LOGGER.warning("Failed to add put option position to watchlist");
                }
            }
        }
    }
}