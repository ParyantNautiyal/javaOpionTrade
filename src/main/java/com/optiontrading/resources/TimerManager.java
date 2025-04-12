package com.optiontrading.resources;

import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.logging.Logger;
import com.google.inject.Inject;
import com.google.inject.Singleton;

/**
 * Centralized manager for all Timer instances in the application.
 * Prevents timer resource leaks by tracking creation and ensuring proper
 * cleanup.
 */
@Singleton
public class TimerManager {
    private static final Logger LOGGER = Logger.getLogger(TimerManager.class.getName());

    // Track all created timers with a reference counter
    private final Map<String, ManagedTimer> managedTimers = new ConcurrentHashMap<>();
    private final AtomicInteger timerCounter = new AtomicInteger(0);

    /**
     * Constructor with dependency injection
     */
    @Inject
    public TimerManager() {
        LOGGER.info("Initialized TimerManager with dependency injection");
    }

    /**
     * Creates a new Timer with the specified name or returns an existing one.
     * Each timer is tracked and will be properly cleaned up during shutdown.
     * 
     * @param name     The name to identify this timer
     * @param isDaemon Whether the timer should run as a daemon thread
     * @return A managed Timer instance
     */
    public Timer createTimer(String name, boolean isDaemon) {
        return managedTimers.computeIfAbsent(name, k -> {
            LOGGER.info("Creating new timer: " + name + " (daemon: " + isDaemon + ")");
            Timer timer = new Timer(name, isDaemon);
            return new ManagedTimer(timer, name);
        }).getTimer();
    }

    /**
     * Creates a new unnamed Timer.
     * Each timer is tracked and will be properly cleaned up during shutdown.
     * 
     * @param isDaemon Whether the timer should run as a daemon thread
     * @return A managed Timer instance
     */
    public Timer createTimer(boolean isDaemon) {
        String name = "Timer-" + timerCounter.incrementAndGet();
        return createTimer(name, isDaemon);
    }

    /**
     * Creates a scheduled task on a managed timer
     * 
     * @param name     The name to identify this timer
     * @param isDaemon Whether the timer should run as a daemon thread
     * @param task     The task to schedule
     * @param delay    Delay in milliseconds before task execution
     * @param period   Period in milliseconds between successive executions
     * @return A managed Timer instance
     */
    public Timer scheduleAtFixedRate(String name, boolean isDaemon, TimerTask task, long delay, long period) {
        Timer timer = createTimer(name, isDaemon);
        timer.scheduleAtFixedRate(task, delay, period);
        return timer;
    }

    /**
     * Cancels and removes a timer by name
     * 
     * @param name The name of the timer to cancel
     * @return true if the timer was found and cancelled, false otherwise
     */
    public boolean cancelTimer(String name) {
        ManagedTimer managedTimer = managedTimers.remove(name);
        if (managedTimer != null) {
            LOGGER.info("Cancelling timer: " + name);
            managedTimer.getTimer().cancel();
            return true;
        }
        return false;
    }

    /**
     * Get the count of active timers
     * 
     * @return The number of active timers
     */
    public int getActiveTimerCount() {
        return managedTimers.size();
    }

    /**
     * Shuts down all active timers
     */
    public void shutdownAllTimers() {
        LOGGER.info("Shutting down all timers (" + managedTimers.size() + " active)");
        for (ManagedTimer managedTimer : managedTimers.values()) {
            managedTimer.getTimer().cancel();
        }
        managedTimers.clear();
    }

    /**
     * Returns metrics about the current timer usage
     * 
     * @return String representation of timer metrics
     */
    public String getTimerMetrics() {
        StringBuilder metrics = new StringBuilder();
        metrics.append("Active timers: ").append(managedTimers.size()).append("\n");

        for (Map.Entry<String, ManagedTimer> entry : managedTimers.entrySet()) {
            metrics.append(" - ").append(entry.getKey())
                    .append(" (created: ").append(entry.getValue().getCreationTime()).append(")\n");
        }

        return metrics.toString();
    }

    /**
     * Internal class to track a Timer with metadata
     */
    private static class ManagedTimer {
        private final Timer timer;
        private final String name;
        private final long creationTime;

        public ManagedTimer(Timer timer, String name) {
            this.timer = timer;
            this.name = name;
            this.creationTime = System.currentTimeMillis();
        }

        public Timer getTimer() {
            return timer;
        }

        public String getName() {
            return name;
        }

        public long getCreationTime() {
            return creationTime;
        }
    }
}