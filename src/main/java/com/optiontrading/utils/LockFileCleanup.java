package com.optiontrading.utils;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Utility class for cleaning up lock files that may be left behind
 * after improper shutdown of the application.
 */
public class LockFileCleanup {
    private static final Logger LOGGER = Logger.getLogger(LockFileCleanup.class.getName());
    private static final String LOG_DIRECTORY = "logs";

    /**
     * Find and clean up any .lck files in the logs directory
     * 
     * @return Number of lock files that were deleted
     */
    public static int cleanupLockFiles() {
        File logDir = new File(LOG_DIRECTORY);
        if (!logDir.exists() || !logDir.isDirectory()) {
            return 0;
        }

        LOGGER.info("Checking for log lock files to clean up...");

        try {
            List<File> lockFiles;
            try (Stream<Path> pathStream = Files.walk(Paths.get(LOG_DIRECTORY))) {
                lockFiles = pathStream
                        .filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .filter(file -> file.getName().endsWith(".lck"))
                        .collect(Collectors.toList());
            }

            int count = 0;
            for (File lockFile : lockFiles) {
                try {
                    if (lockFile.delete()) {
                        LOGGER.info("Deleted lock file: " + lockFile.getAbsolutePath());
                        count++;
                    } else {
                        LOGGER.warning("Failed to delete lock file: " + lockFile.getAbsolutePath());
                    }
                } catch (Exception e) {
                    LOGGER.log(Level.WARNING, "Error deleting lock file: " + lockFile.getAbsolutePath(), e);
                }
            }

            if (count > 0) {
                LOGGER.info("Cleaned up " + count + " lock files");
            } else {
                LOGGER.info("No lock files found to clean up");
            }

            return count;
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error searching for lock files", e);
            return 0;
        }
    }

    /**
     * Find and list any .lck files that exist
     * 
     * @return List of lock file paths
     */
    public static List<String> findLockFiles() {
        File logDir = new File(LOG_DIRECTORY);
        if (!logDir.exists() || !logDir.isDirectory()) {
            return Arrays.asList();
        }

        try {
            try (Stream<Path> pathStream = Files.walk(Paths.get(LOG_DIRECTORY))) {
                return pathStream
                        .filter(Files::isRegularFile)
                        .map(Path::toFile)
                        .filter(file -> file.getName().endsWith(".lck"))
                        .map(File::getAbsolutePath)
                        .collect(Collectors.toList());
            }
        } catch (IOException e) {
            LOGGER.log(Level.WARNING, "Error searching for lock files", e);
            return Arrays.asList();
        }
    }
}