package com.app.algodesk.models;

import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

// One Kite historical-candles request. Bound straight from HTTP by Spring:
// query/path parameters for GET (via the setters), a JSON body for POST (via Jackson).
// Dates are whole days; CandleService turns them into 00:00:00 .. 23:59:59 for Kite.
public class HistoricalDataRequest {
    private String exchange;            // NSE, BSE, NFO, MCX ...
    private String symbol;              // tradingsymbol, e.g. RELIANCE
    private String interval = "day";    // minute, 3minute, 5minute, ... day
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate from;             // optional: null = about 500 candles back from 'to'
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    private LocalDate to;               // optional: null = today
    private boolean continuous;         // futures only (NFO/MCX): joins expired contracts into one series
    private boolean oi;                 // adds open interest as a 7th value in every candle

    public HistoricalDataRequest() {
    }

    public HistoricalDataRequest(String exchange, String symbol, String interval, LocalDate from, LocalDate to) {
        this.exchange = exchange;
        this.symbol = symbol;
        this.interval = interval;
        this.from = from;
        this.to = to;
    }

    public String getExchange() { return exchange; }
    public void setExchange(String exchange) { this.exchange = exchange; }

    public String getSymbol() { return symbol; }
    public void setSymbol(String symbol) { this.symbol = symbol; }

    public String getInterval() { return interval; }
    public void setInterval(String interval) { this.interval = interval; }

    public LocalDate getFrom() { return from; }
    public void setFrom(LocalDate from) { this.from = from; }

    public LocalDate getTo() { return to; }
    public void setTo(LocalDate to) { this.to = to; }

    public boolean isContinuous() { return continuous; }
    public void setContinuous(boolean continuous) { this.continuous = continuous; }

    public boolean isOi() { return oi; }
    public void setOi(boolean oi) { this.oi = oi; }
}
