package com.optiontrading.service.position;

/**
 * Indicates the status of a position in the watchlist
 */
public enum PositionStatus {
    /**
     * Position is active and being monitored
     */
    ACTIVE,

    /**
     * Position price trigger has been reached
     */
    TRIGGERED,

    /**
     * Position stop loss has been reached
     */
    STOPPED_OUT,

    /**
     * Position has been manually closed by user
     */
    CLOSED,

    /**
     * Position has been exited due to error
     */
    ERROR
}