package com.optiontrading;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.optiontrading.config.ConfigurationManager;
import com.optiontrading.di.AppModule;
import com.optiontrading.service.model.OrderScheduleParams;
import com.optiontrading.service.model.OrderStatus;
import com.optiontrading.service.model.ScheduledOrder;
import com.optiontrading.service.order.OrderRepository;
import com.optiontrading.service.order.OrderExecutionCoordinator;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Scanner;
import java.util.logging.Logger;

/**
 * Test class to demonstrate order scheduling flow.
 * This class is designed to be run with Maven to ensure all dependencies
 * are properly available:
 * 
 * mvn exec:java -Dexec.mainClass="com.optiontrading.TestOrderScheduling"
 */
public class TestOrderScheduling {
    private static final Logger LOGGER = Logger.getLogger(TestOrderScheduling.class.getName());

    public static void main(String[] args) {
        System.out.println("=========================================");
        System.out.println("= OPTION TRADING ORDER SCHEDULING TEST =");
        System.out.println("=========================================");

        try {
            // Initialize Guice injector with the app module
            Injector injector = Guice.createInjector(new AppModule());

            // Get necessary services from Guice
            OrderRepository orderRepository = injector.getInstance(OrderRepository.class);
            OrderExecutionCoordinator coordinator = injector.getInstance(OrderExecutionCoordinator.class);
            ConfigurationManager configManager = injector.getInstance(ConfigurationManager.class);

            // Create a scheduled order for execution 1 minute from now
            LocalDateTime executionTime = LocalDateTime.now().plusMinutes(1);
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("dd-MMM-yyyy HH:mm:ss");
            System.out.println("\nCreating order for execution at: " + executionTime.format(formatter));

            // Create order parameters using defaults from config
            OrderScheduleParams params = OrderScheduleParams.builderWithDefaults(configManager)
                    .indexSymbol("NIFTY")
                    .expiryDate(LocalDate.now().plusDays(7)) // Next week's expiry
                    .targetPremium(new BigDecimal("50")) // Target premium of 50
                    .lots(1)
                    .executionTime(executionTime)
                    .build();

            // Create the order
            ScheduledOrder order = orderRepository.createOrder(params);
            System.out.println("Created order with ID: " + order.getOrderId());
            System.out.println("Initial status: " + order.getStatus());

            // Monitor order status
            System.out.println("\nMonitoring order status (press Enter to exit)...");
            Thread monitorThread = startMonitoringThread(orderRepository, order);

            // Wait for user to press Enter to exit
            Scanner scanner = new Scanner(System.in);
            scanner.nextLine();

            monitorThread.interrupt();
            System.out.println("Test completed.");
        } catch (Exception e) {
            System.err.println("Error during test: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private static Thread startMonitoringThread(OrderRepository orderRepository, ScheduledOrder order) {
        Thread thread = new Thread(() -> {
            try {
                boolean orderProcessed = false;
                OrderStatus lastStatus = order.getStatus();

                while (!orderProcessed && !Thread.currentThread().isInterrupted()) {
                    // Check every 2 seconds
                    Thread.sleep(2000);

                    // Get updated order
                    ScheduledOrder refreshedOrder = orderRepository.getOrder(order.getOrderId());
                    if (refreshedOrder == null) {
                        System.out.println("ERROR: Order no longer exists!");
                        break;
                    }

                    OrderStatus currentStatus = refreshedOrder.getStatus();

                    // Display status info if changed
                    if (currentStatus != lastStatus) {
                        System.out.println("*** STATUS CHANGED: " + currentStatus + " ***");
                        lastStatus = currentStatus;
                    }

                    // Display time info
                    LocalDateTime now = LocalDateTime.now();
                    LocalDateTime executionTime = refreshedOrder.getExecutionTime();
                    long secondsRemaining = java.time.temporal.ChronoUnit.SECONDS.between(now, executionTime);

                    if (secondsRemaining % 5 == 0 || secondsRemaining <= 10) { // Show more updates during final
                                                                               // countdown
                        System.out.println(String.format("[%s] Status: %s %s",
                                now.format(DateTimeFormatter.ofPattern("HH:mm:ss")),
                                currentStatus,
                                secondsRemaining > 0 ? "(" + secondsRemaining + " seconds until execution)"
                                        : "(execution time passed)"));
                    }

                    // Check if order processing is complete
                    if (currentStatus == OrderStatus.COMPLETED || currentStatus == OrderStatus.FAILED) {
                        System.out.println("Order processing completed with status: " + currentStatus);
                        orderProcessed = true;
                    }
                }
            } catch (InterruptedException e) {
                // Thread interrupted, exit gracefully
                Thread.currentThread().interrupt();
            }
        });

        thread.setDaemon(true);
        thread.start();
        return thread;
    }
}