package com.optiontrading.service.position;

/**
 * Indicates the source of a position in the watchlist
 */
public enum PositionSource {
    /**
     * Position was manually added by user
     */
    MANUAL,

    /**
     * Position was added by the strategy/option chain system
     */
    STRATEGY
}