package com.optiontrading;

/**
 * Simple entry point for the option trading application.
 */
public class OptionTradingApp {
    public static void main(String[] args) {
        System.out.println("Starting Option Trading application");

        // Initialize application
        Main.main(args);
    }

    public static void shutdown() {
        System.out.println("Shutting down Option Trading application");

        // Application cleanup happens in Main class
    }
}