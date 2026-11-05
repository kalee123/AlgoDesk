package com.app.algodesk.models;

// One OHLC candle. time is the candle's start in epoch seconds (same as the candles API).
// Prices are double, not BigDecimal: backtests do lots of indicator maths (averages, ratios)
// where tiny rounding doesn't matter, and BigDecimal would make every formula hard to read.
public class CandleInfo {
    private long time;
    private double open;
    private double high;
    private double low;
    private double close;
    private long volume;
    private Long oi;   // open interest, F&O only; null unless requested (then left out of the JSON)

    public CandleInfo() {
    }

    public CandleInfo(long time, double open, double high, double low, double close, long volume) {
        this.time = time;
        this.open = open;
        this.high = high;
        this.low = low;
        this.close = close;
        this.volume = volume;
    }

    public long getTime() { return time; }
    public void setTime(long time) { this.time = time; }

    public double getOpen() { return open; }
    public void setOpen(double open) { this.open = open; }

    public double getHigh() { return high; }
    public void setHigh(double high) { this.high = high; }

    public double getLow() { return low; }
    public void setLow(double low) { this.low = low; }

    public double getClose() { return close; }
    public void setClose(double close) { this.close = close; }

    public long getVolume() { return volume; }
    public void setVolume(long volume) { this.volume = volume; }

    public Long getOi() { return oi; }
    public void setOi(Long oi) { this.oi = oi; }
}
