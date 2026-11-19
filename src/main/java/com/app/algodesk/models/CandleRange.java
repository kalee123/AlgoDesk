package com.app.algodesk.models;

import com.app.algodesk.appsetup.Intervals;

import java.time.LocalDateTime;

// One Kite historical-candles call, already checked: a valid interval and exact start/end times.
// Built by CandleService from a HistoricalDataRequest.
public class CandleRange {

    private Intervals interval;
    private LocalDateTime startTime;
    private LocalDateTime endTime;
    private boolean continuous;
    private boolean oi;

    public CandleRange() {
    }

    public CandleRange(Intervals interval, LocalDateTime startTime, LocalDateTime endTime,
                       boolean continuous, boolean oi) {
        this.interval = interval;
        this.startTime = startTime;
        this.endTime = endTime;
        this.continuous = continuous;
        this.oi = oi;
    }

    public Intervals getInterval() { return interval; }
    public void setInterval(Intervals interval) { this.interval = interval; }

    public LocalDateTime getStartTime() { return startTime; }
    public void setStartTime(LocalDateTime startTime) { this.startTime = startTime; }

    public LocalDateTime getEndTime() { return endTime; }
    public void setEndTime(LocalDateTime endTime) { this.endTime = endTime; }

    public boolean isContinuous() { return continuous; }
    public void setContinuous(boolean continuous) { this.continuous = continuous; }

    public boolean isOi() { return oi; }
    public void setOi(boolean oi) { this.oi = oi; }
}
