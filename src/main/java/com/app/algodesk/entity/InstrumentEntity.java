package com.app.algodesk.entity;

import jakarta.persistence.*;

import java.math.BigDecimal;
import java.time.LocalDate;

// One row per tradable instrument from Kite's instrument list (stocks, indices, futures, options...).
// (exchange, tradingsymbol) is the identity: exchanges can reuse instrument_token for a different
// derivative after expiry, so the token is only an indexed lookup column, never the key.
@Entity
@Table(name = "instruments",
        uniqueConstraints = @UniqueConstraint(name = "uk_instruments_exchange_symbol",
                columnNames = {"exchange", "tradingsymbol"}),
        indexes = {
                @Index(name = "idx_instruments_token", columnList = "instrument_token"),
                @Index(name = "idx_instruments_name", columnList = "name")
        })
public class InstrumentEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    // Kite's id for the instrument, used when subscribing on the WebSocket
    @Column(name = "instrument_token", nullable = false)
    private Long instrumentToken;

    @Column(name = "exchange_token", nullable = false)
    private Long exchangeToken;

    @Column(name = "tradingsymbol", nullable = false, length = 50)
    private String tradingsymbol;

    @Column(name = "name", length = 150)
    private String name;

    @Column(name = "last_price", precision = 14, scale = 4)
    private BigDecimal lastPrice;

    // Only set for futures and options
    @Column(name = "expiry")
    private LocalDate expiry;

    // Only set for options
    @Column(name = "strike", precision = 14, scale = 4)
    private BigDecimal strike;

    @Column(name = "tick_size", precision = 10, scale = 4)
    private BigDecimal tickSize;

    @Column(name = "lot_size")
    private Integer lotSize;

    // EQ, FUT, CE, PE
    @Column(name = "instrument_type", length = 10)
    private String instrumentType;

    // e.g. NSE, NFO-OPT, INDICES
    @Column(name = "segment", length = 20)
    private String segment;

    // e.g. NSE, BSE, NFO, MCX
    @Column(name = "exchange", nullable = false, length = 10)
    private String exchange;

    public Long getId() {
        return id;
    }

    public Long getInstrumentToken() {
        return instrumentToken;
    }
    public void setInstrumentToken(Long instrumentToken) {
        this.instrumentToken = instrumentToken;
    }

    public Long getExchangeToken() {
        return exchangeToken;
    }
    public void setExchangeToken(Long exchangeToken) {
        this.exchangeToken = exchangeToken;
    }

    public String getTradingsymbol() {
        return tradingsymbol;
    }
    public void setTradingsymbol(String tradingsymbol) {
        this.tradingsymbol = tradingsymbol;
    }

    public String getName() {
        return name;
    }
    public void setName(String name) {
        this.name = name;
    }

    public BigDecimal getLastPrice() {
        return lastPrice;
    }
    public void setLastPrice(BigDecimal lastPrice) {
        this.lastPrice = lastPrice;
    }

    public LocalDate getExpiry() {
        return expiry;
    }
    public void setExpiry(LocalDate expiry) {
        this.expiry = expiry;
    }

    public BigDecimal getStrike() {
        return strike;
    }
    public void setStrike(BigDecimal strike) {
        this.strike = strike;
    }

    public BigDecimal getTickSize() {
        return tickSize;
    }
    public void setTickSize(BigDecimal tickSize) {
        this.tickSize = tickSize;
    }

    public Integer getLotSize() {
        return lotSize;
    }
    public void setLotSize(Integer lotSize) {
        this.lotSize = lotSize;
    }

    public String getInstrumentType() {
        return instrumentType;
    }
    public void setInstrumentType(String instrumentType) {
        this.instrumentType = instrumentType;
    }

    public String getSegment() {
        return segment;
    }
    public void setSegment(String segment) {
        this.segment = segment;
    }

    public String getExchange() {
        return exchange;
    }
    public void setExchange(String exchange) {
        this.exchange = exchange;
    }
}
