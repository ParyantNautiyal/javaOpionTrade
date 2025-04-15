package com.optiontrading.ui;

import com.optiontrading.di.ApplicationContext;

/**
 * Utility class to open the positions window
 */
public class PositionsWindowOpener {

    private static boolean initialized = false;

    /**
     * Open the positions window
     * 
     * @param appContext the application context
     */
    public static void openPositionsWindow(ApplicationContext appContext) {
        if (!initialized) {
            System.out.println("Initializing JavaFX application...");
            // Initialize the JavaFX application
            PositionsApplication.initialize(appContext);
            initialized = true;
        }

        // Show the positions window
        PositionsApplication.showPositionsWindow();
    }
}