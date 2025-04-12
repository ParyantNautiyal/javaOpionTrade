package com.optiontrading.resources;

import java.io.File;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.logging.FileHandler;
import java.util.logging.Formatter;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import java.util.List;

/**
 * Monitors application-specific resources and logs usage statistics
 * Creates a dedicated log file for resource usage tracking
 * This class is completely independent of other application components
 */
public class ResourceMonitor {
    private static final Logger LOGGER = Logger.getLogger(ResourceMonitor.class.getName());
    private static final String LOG_DIR = "logs";
    private static final String RESOURCE_LOG_FILE = LOG_DIR + "/resource_usage.log";
    private static ResourceMonitor INSTANCE;

    private final ScheduledExecutorService scheduler;
    private final MemoryMXBean memoryBean;
    private final ThreadMXBean threadBean;
    private final Logger resourceLogger;
    private FileHandler fileHandler;

    private final SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    // Private constructor for singleton
    private ResourceMonitor() {
        this.scheduler = Executors.newScheduledThreadPool(1);
        this.memoryBean = ManagementFactory.getMemoryMXBean();
        this.threadBean = ManagementFactory.getThreadMXBean();

        // Create dedicated logger for resource monitoring
        this.resourceLogger = Logger.getLogger("com.optiontrading.resources.usage");
        this.resourceLogger.setUseParentHandlers(false);

        try {
            // Ensure log directory exists
            Files.createDirectories(Paths.get(LOG_DIR));

            // Configure file handler
            fileHandler = new FileHandler(RESOURCE_LOG_FILE, 10 * 1024 * 1024, 5, true);
            fileHandler.setFormatter(new ResourceLogFormatter());
            this.resourceLogger.addHandler(fileHandler);

            LOGGER.info("ResourceMonitor initialized, logging to " + RESOURCE_LOG_FILE);
        } catch (Exception e) {
            LOGGER.log(Level.SEVERE, "Error setting up resource log file", e);
        }
    }

    /**
     * Get the singleton instance
     */
    public static synchronized ResourceMonitor getInstance() {
        if (INSTANCE == null) {
            INSTANCE = new ResourceMonitor();
        }
        return INSTANCE;
    }

    /**
     * Start monitoring resources
     * 
     * @param intervalSeconds interval between log entries in seconds
     */
    public void startMonitoring(int intervalSeconds) {
        LOGGER.info("Starting resource monitoring with " + intervalSeconds + " second interval");

        scheduler.scheduleAtFixedRate(() -> {
            try {
                logResourceUsage();
            } catch (Exception e) {
                LOGGER.log(Level.WARNING, "Error logging resource usage", e);
            }
        }, 0, intervalSeconds, TimeUnit.SECONDS);
    }

    /**
     * Stop monitoring resources
     */
    public void stopMonitoring() {
        LOGGER.info("Stopping resource monitoring");
        scheduler.shutdown();
        try {
            if (fileHandler != null) {
                fileHandler.close();
            }
        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error closing resource log file handler", e);
        }
    }

    /**
     * Log current resource usage
     */
    private void logResourceUsage() {
        try {
            StringBuilder metrics = new StringBuilder();

            // Time
            metrics.append("Time: ").append(dateFormat.format(new Date())).append(", ");

            // Application Memory
            long usedHeap = memoryBean.getHeapMemoryUsage().getUsed();
            long maxHeap = memoryBean.getHeapMemoryUsage().getMax();
            long committedHeap = memoryBean.getHeapMemoryUsage().getCommitted();

            metrics.append("Heap Memory: ").append(formatBytes(usedHeap)).append(" / ")
                    .append(formatBytes(maxHeap)).append(" (")
                    .append(String.format("%.2f", ((double) usedHeap / maxHeap) * 100)).append("%), ");

            // Heap commit ratio (shows potential memory pressure)
            metrics.append("Heap Commit: ").append(formatBytes(committedHeap)).append(" (")
                    .append(String.format("%.2f", ((double) committedHeap / maxHeap) * 100)).append("%), ");

            // Trading application specific metrics
            metrics.append("Trading App: ");

            // Thread counts (for detecting thread leaks)
            int threadCount = threadBean.getThreadCount();
            int daemonThreads = threadBean.getDaemonThreadCount();
            metrics.append("Threads: ").append(threadCount)
                    .append(" (daemon: ").append(daemonThreads).append("), ");

            // Application data directories
            logDirectoryStats(metrics, "data/instruments", "Instruments");
            logDirectoryStats(metrics, "config", "Config");
            logDirectoryStats(metrics, "logs", "Logs");

            // Track garbage collection efficiency
            List<java.lang.management.GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
            long totalGCCount = 0;
            long totalGCTime = 0;

            for (java.lang.management.GarbageCollectorMXBean gcBean : gcBeans) {
                totalGCCount += gcBean.getCollectionCount();
                totalGCTime += gcBean.getCollectionTime();
            }

            // GC Pressure - high values indicate memory pressure
            metrics.append("GC Stats: count=").append(totalGCCount)
                    .append(", time=").append(totalGCTime).append("ms");

            // Log the metrics
            resourceLogger.info(metrics.toString());

        } catch (Exception e) {
            LOGGER.log(Level.WARNING, "Error collecting resource metrics", e);
        }
    }

    /**
     * Log stats for a specific application directory
     */
    private void logDirectoryStats(StringBuilder metrics, String dirPath, String label) {
        File dir = new File(dirPath);
        if (dir.exists() && dir.isDirectory()) {
            int fileCount = countFiles(dir);
            long dirSize = calculateDirectorySize(dir);

            metrics.append(label).append("(").append(fileCount).append(" files, ")
                    .append(formatBytes(dirSize)).append("), ");
        }
    }

    /**
     * Calculate total size of a directory and its contents
     */
    private long calculateDirectorySize(File directory) {
        long size = 0;
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        size += file.length();
                    } else if (file.isDirectory()) {
                        size += calculateDirectorySize(file);
                    }
                }
            }
        }
        return size;
    }

    /**
     * Count total files in a directory recursively
     */
    private int countFiles(File directory) {
        int count = 0;
        if (directory.exists()) {
            File[] files = directory.listFiles();
            if (files != null) {
                for (File file : files) {
                    if (file.isFile()) {
                        count++;
                    } else if (file.isDirectory()) {
                        count += countFiles(file);
                    }
                }
            }
        }
        return count;
    }

    /**
     * Format bytes to human-readable format
     */
    private String formatBytes(long bytes) {
        if (bytes < 1024)
            return bytes + " B";
        int exp = (int) (Math.log(bytes) / Math.log(1024));
        String pre = "KMGTPE".charAt(exp - 1) + "";
        return String.format("%.1f %sB", bytes / Math.pow(1024, exp), pre);
    }

    /**
     * Custom formatter for resource logs
     */
    private static class ResourceLogFormatter extends Formatter {
        @Override
        public String format(LogRecord record) {
            return record.getMessage() + System.lineSeparator();
        }
    }
}