package com.optiontrading.service.model;

/**
 * Status of a scheduled order
 */
public enum OrderStatus {
    SCHEDULED, // Order is scheduled but not yet in execution process
    PREPARING, // T-25: Starting to prepare for execution
    ANALYZING, // Analyzing option chains
    MONITORING, // Monitoring option chain for best options
    HEDGING, // T-10: Executing hedge orders
    EXECUTING, // T-0: Executing main orders
    COMPLETED, // All orders executed successfully
    CANCELLED, // Order was cancelled by user
    FAILED, // Execution failed
    PENDING, // Order is placed but not yet completed/failed
    UNKNOWN // Could not determine the current status
}