package com.app.algodesk.models;

import java.time.LocalDateTime;

public class HistoricalDataRequest {
    private String exchange;      // NSE, BSE, NFO, MCX ...
    private String symbol;        // tradingsymbol, e.g. RELIANCE
    private String interval;      // minute, 3minute, 5minute, ... day
    private LocalDateTime from;
    private LocalDateTime to;
    private boolean continuous;   // futures only (NFO/MCX): joins expired contracts into one series
    private boolean oi;           // adds open interest as a 7th value in every candle

    public HistoricalDataRequest() {
    }

    public HistoricalDataRequest(String exchange, String symbol, String interval,
                                 LocalDateTime from, LocalDateTime to) {
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

    public LocalDateTime getFrom() { return from; }
    public void setFrom(LocalDateTime from) { this.from = from; }

    public LocalDateTime getTo() { return to; }
    public void setTo(LocalDateTime to) { this.to = to; }

    public boolean isContinuous() { return continuous; }
    public void setContinuous(boolean continuous) { this.continuous = continuous; }

    public boolean isOi() { return oi; }
    public void setOi(boolean oi) { this.oi = oi; }
}
