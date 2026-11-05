package com.app.algodesk.models;

import java.time.LocalDate;

// One simulated trade from a backtest. Times are epoch seconds, like CandleInfo,
// so the chart can later draw entry/exit markers on the same candles.
public class Trade {

    public enum Direction { LONG, SHORT }

    private final LocalDate date;
    private final Direction direction;
    private final double rangeHigh;
    private final double rangeLow;
    private final long entryTime;
    private final double entryPrice;
    private final double stopLoss;
    private final double target;
    private long exitTime;
    private double exitPrice;
    private String exitReason;   // TARGET, STOP_LOSS, SQUARE_OFF, DAY_END

    public Trade(LocalDate date, Direction direction, double rangeHigh, double rangeLow,
                 long entryTime, double entryPrice, double stopLoss, double target) {
        this.date = date;
        this.direction = direction;
        this.rangeHigh = rangeHigh;
        this.rangeLow = rangeLow;
        this.entryTime = entryTime;
        this.entryPrice = entryPrice;
        this.stopLoss = stopLoss;
        this.target = target;
    }

    public void close(long exitTime, double exitPrice, String exitReason) {
        this.exitTime = exitTime;
        this.exitPrice = exitPrice;
        this.exitReason = exitReason;
    }

    // Points per share; positive is a profit for both LONG and SHORT
    public double getPnl() {
        return direction == Direction.LONG ? exitPrice - entryPrice : entryPrice - exitPrice;
    }

    // P&L as a % of the entry price, so trades on cheap and expensive stocks compare fairly
    public double getPnlPercent() {
        return getPnl() / entryPrice * 100;
    }

    public LocalDate getDate() { return date; }
    public Direction getDirection() { return direction; }
    public double getRangeHigh() { return rangeHigh; }
    public double getRangeLow() { return rangeLow; }
    public long getEntryTime() { return entryTime; }
    public double getEntryPrice() { return entryPrice; }
    public double getStopLoss() { return stopLoss; }
    public double getTarget() { return target; }
    public long getExitTime() { return exitTime; }
    public double getExitPrice() { return exitPrice; }
    public String getExitReason() { return exitReason; }
}
