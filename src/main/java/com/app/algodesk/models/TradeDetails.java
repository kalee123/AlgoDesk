package com.app.algodesk.models;

import java.time.LocalDate;

// One trade from a backtest: filled step by step (setters, no constructor), then closed.
public class TradeDetails {

    public enum Direction { LONG, SHORT }

    private LocalDate date;
    private Direction direction;
    private double rangeHigh;
    private double rangeLow;
    private long entryTime;
    private double entryPrice;
    private double stopLoss;
    private double target;
    private long exitTime;
    private double exitPrice;
    private String exitReason;   // TARGET, STOP_LOSS, SQUARE_OFF, DAY_END

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public Direction getDirection() { return direction; }
    public void setDirection(Direction direction) { this.direction = direction; }

    public double getRangeHigh() { return rangeHigh; }
    public void setRangeHigh(double rangeHigh) { this.rangeHigh = rangeHigh; }

    public double getRangeLow() { return rangeLow; }
    public void setRangeLow(double rangeLow) { this.rangeLow = rangeLow; }

    public long getEntryTime() { return entryTime; }
    public void setEntryTime(long entryTime) { this.entryTime = entryTime; }

    public double getEntryPrice() { return entryPrice; }
    public void setEntryPrice(double entryPrice) { this.entryPrice = entryPrice; }

    public double getStopLoss() { return stopLoss; }
    public void setStopLoss(double stopLoss) { this.stopLoss = stopLoss; }

    public double getTarget() { return target; }
    public void setTarget(double target) { this.target = target; }

    public long getExitTime() { return exitTime; }
    public void setExitTime(long exitTime) { this.exitTime = exitTime; }

    public double getExitPrice() { return exitPrice; }
    public void setExitPrice(double exitPrice) { this.exitPrice = exitPrice; }

    public String getExitReason() { return exitReason; }
    public void setExitReason(String exitReason) { this.exitReason = exitReason; }

    // Sets the three exit fields together, so a trade can't end up with a price but no reason
    public void close(long exitTime, double exitPrice, String exitReason) {
        this.exitTime = exitTime;
        this.exitPrice = exitPrice;
        this.exitReason = exitReason;
    }

    // Points per share; positive is a profit for both LONG and SHORT. Only meaningful once the exit is set.
    public double getPnl() {
        return direction == Direction.LONG ? exitPrice - entryPrice : entryPrice - exitPrice;
    }

    // P&L as a % of the entry price; 0 before entry, so it never divides by zero
    public double getPnlPercent() {
        return entryPrice == 0 ? 0 : getPnl() / entryPrice * 100;
    }
}
