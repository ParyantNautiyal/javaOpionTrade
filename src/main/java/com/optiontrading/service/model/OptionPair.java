package com.optiontrading.service.model;

import java.math.BigDecimal;
import java.util.Objects;

/**
 * Represents a pair of call and put options with their current prices
 */
public class OptionPair {
    private final Instrument callOption;
    private final Instrument putOption;
    private final BigDecimal callPrice;
    private final BigDecimal putPrice;

    public OptionPair(Instrument callOption, BigDecimal callPrice,
            Instrument putOption, BigDecimal putPrice) {
        if (callOption != null && !callOption.isCall()) {
            throw new IllegalArgumentException("Call option must be of type CALL");
        }
        if (putOption != null && !putOption.isPut()) {
            throw new IllegalArgumentException("Put option must be of type PUT");
        }

        this.callOption = callOption;
        this.callPrice = callPrice;
        this.putOption = putOption;
        this.putPrice = putPrice;
    }

    public Instrument getCallOption() {
        return callOption;
    }

    public Instrument getPutOption() {
        return putOption;
    }

    public BigDecimal getCallPrice() {
        return callPrice;
    }

    public BigDecimal getPutPrice() {
        return putPrice;
    }

    public boolean hasCallOption() {
        return callOption != null;
    }

    public boolean hasPutOption() {
        return putOption != null;
    }

    public boolean isComplete() {
        return hasCallOption() && hasPutOption();
    }

    /**
     * Calculates the absolute difference between target premium and current options
     * 
     * @param targetPremium the target premium value
     * @return the absolute difference
     */
    public BigDecimal getPremiumDifference(BigDecimal targetPremium) {
        if (targetPremium == null) {
            return BigDecimal.ZERO;
        }

        if (hasCallOption() && hasPutOption()) {
            // Average of both premiums
            BigDecimal avgPremium = callPrice.add(putPrice).divide(BigDecimal.valueOf(2), 2,
                    BigDecimal.ROUND_HALF_UP);
            return targetPremium.subtract(avgPremium).abs();
        } else if (hasCallOption()) {
            return targetPremium.subtract(callPrice).abs();
        } else if (hasPutOption()) {
            return targetPremium.subtract(putPrice).abs();
        }

        return BigDecimal.valueOf(Double.MAX_VALUE);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        OptionPair that = (OptionPair) o;
        return Objects.equals(callOption, that.callOption) &&
                Objects.equals(putOption, that.putOption);
    }

    @Override
    public int hashCode() {
        return Objects.hash(callOption, putOption);
    }

    @Override
    public String toString() {
        return "OptionPair{" +
                "call=" + (callOption != null ? callOption.getTradingSymbol() + "@" + callPrice : "null") +
                ", put=" + (putOption != null ? putOption.getTradingSymbol() + "@" + putPrice : "null") +
                '}';
    }
}