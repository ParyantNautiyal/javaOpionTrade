package com.optiontrading.service.model;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;

/**
 * Represents a tradable instrument in the market
 */
public class Instrument {
    private final String instrumentId;
    private final String tradingSymbol;
    private final String exchange;
    private final InstrumentType type;
    private final String underlyingSymbol;
    private final LocalDate expiryDate;
    private final BigDecimal strikePrice;
    private final OptionType optionType;
    private final int lotSize;

    private Instrument(Builder builder) {
        this.instrumentId = builder.instrumentId;
        this.tradingSymbol = builder.tradingSymbol;
        this.exchange = builder.exchange;
        this.type = builder.type;
        this.underlyingSymbol = builder.underlyingSymbol;
        this.expiryDate = builder.expiryDate;
        this.strikePrice = builder.strikePrice;
        this.optionType = builder.optionType;
        this.lotSize = builder.lotSize;
    }

    public String getInstrumentId() {
        return instrumentId;
    }

    public String getTradingSymbol() {
        return tradingSymbol;
    }

    public String getExchange() {
        return exchange;
    }

    public InstrumentType getType() {
        return type;
    }

    public String getUnderlyingSymbol() {
        return underlyingSymbol;
    }

    public LocalDate getExpiryDate() {
        return expiryDate;
    }

    public BigDecimal getStrikePrice() {
        return strikePrice;
    }

    public OptionType getOptionType() {
        return optionType;
    }

    public int getLotSize() {
        return lotSize;
    }

    public boolean isOption() {
        return type == InstrumentType.OPTION;
    }

    public boolean isCall() {
        return isOption() && optionType == OptionType.CALL;
    }

    public boolean isPut() {
        return isOption() && optionType == OptionType.PUT;
    }

    public static Builder builder() {
        return new Builder();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o)
            return true;
        if (o == null || getClass() != o.getClass())
            return false;
        Instrument that = (Instrument) o;
        return Objects.equals(instrumentId, that.instrumentId);
    }

    @Override
    public int hashCode() {
        return Objects.hash(instrumentId);
    }

    @Override
    public String toString() {
        return "Instrument{" +
                "instrumentId='" + instrumentId + '\'' +
                ", tradingSymbol='" + tradingSymbol + '\'' +
                ", type=" + type +
                (isOption() ? ", optionType=" + optionType + ", strike=" + strikePrice : "") +
                ", expiry=" + expiryDate +
                '}';
    }

    public static class Builder {
        private String instrumentId;
        private String tradingSymbol;
        private String exchange;
        private InstrumentType type;
        private String underlyingSymbol;
        private LocalDate expiryDate;
        private BigDecimal strikePrice;
        private OptionType optionType;
        private int lotSize;

        public Builder instrumentId(String instrumentId) {
            this.instrumentId = instrumentId;
            return this;
        }

        public Builder tradingSymbol(String tradingSymbol) {
            this.tradingSymbol = tradingSymbol;
            return this;
        }

        public Builder exchange(String exchange) {
            this.exchange = exchange;
            return this;
        }

        public Builder type(InstrumentType type) {
            this.type = type;
            return this;
        }

        public Builder underlyingSymbol(String underlyingSymbol) {
            this.underlyingSymbol = underlyingSymbol;
            return this;
        }

        public Builder expiryDate(LocalDate expiryDate) {
            this.expiryDate = expiryDate;
            return this;
        }

        public Builder strikePrice(BigDecimal strikePrice) {
            this.strikePrice = strikePrice;
            return this;
        }

        public Builder optionType(OptionType optionType) {
            this.optionType = optionType;
            return this;
        }

        public Builder lotSize(int lotSize) {
            this.lotSize = lotSize;
            return this;
        }

        public Instrument build() {
            return new Instrument(this);
        }
    }
}