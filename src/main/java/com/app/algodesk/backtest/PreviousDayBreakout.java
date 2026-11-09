package com.app.algodesk.backtest;

import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.Trade;
import com.app.algodesk.models.Trade.Direction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

// Previous Day High/Low breakout on intraday candles, at most one trade per day:
//  1. Levels: PDH / PDL = highest high / lowest low of the previous trading day, built from the same candles
//  2. Signal: first candle that CLOSES above PDH (long) or below PDL (short), before 15:00
//  3. Entry: the NEXT candle's open
//  4. Stop: the signal candle's low (long) / high (short), but at least MIN_RISK_PERCENT away.
//     If the risk is more than MAX_RISK_PERCENT the entry is too far from the level: skip the day.
//     Target: 2 x risk. Square off at 15:15.
// Gap days: if the day's first candle already opens beyond a level, the breakout happened overnight; skip the day.
// The first day in the data has no previous day, so it only builds levels.
// Deliberately a copy of OpeningRangeBreakout's trade management for now; the shared engine is extracted
// once a third strategy shows what is really common.
@Component("pdb")
public class PreviousDayBreakout implements TradingStrategy {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final LocalTime LAST_SIGNAL = LocalTime.of(15, 0);
    static final LocalTime SQUARE_OFF = LocalTime.of(15, 15);
    static final double REWARD_TO_RISK = 2.0;
    static final double MIN_RISK_PERCENT = 0.3;   // stop never tighter than this (noise)
    static final double MAX_RISK_PERCENT = 1.5;   // wider than this means we're chasing: no trade

    private enum Phase { WAIT_BREAKOUT, IN_TRADE, DONE }

    private final List<Trade> trades = new ArrayList<>();
    private LocalDate day;
    private Phase phase;
    // Yesterday's levels (what we trade against) and today's extremes (tomorrow's levels)
    private double pdh = Double.NaN;
    private double pdl = Double.NaN;
    private double todayHigh;
    private double todayLow;
    private Direction pendingEntry;
    private double signalStop;        // signal candle's low (long) or high (short)
    private Trade trade;
    private CandleInfo previous;

    @Override
    public String name() {
        return "Previous Day High/Low Breakout";
    }

    @Override
    public String description() {
        return "Levels = previous day's high and low · enter on the first close beyond one · stop = signal candle's "
                + "low/high (min 0.3%, skip above 1.5%) · target 2× risk · square off 15:15 · gap days skipped.";
    }

    // candles must be oldest first (as Kite returns them).
    // The Spring bean is a singleton shared by every request, so each run uses a fresh instance for its state.
    @Override
    public List<Trade> run(List<CandleInfo> candles) {
        PreviousDayBreakout pdb = new PreviousDayBreakout();
        for (CandleInfo candle : candles) {
            pdb.onCandle(candle);
        }
        pdb.closeAtDayEnd();
        return pdb.trades;
    }

    private void onCandle(CandleInfo candle) {
        ZonedDateTime t = Instant.ofEpochSecond(candle.getTime()).atZone(IST);
        LocalTime time = t.toLocalTime();

        if (!t.toLocalDate().equals(day)) {
            closeAtDayEnd();
            startDay(t.toLocalDate(), candle);
        }
        // Every candle counts towards tomorrow's levels, even after today's trading is DONE
        todayHigh = Math.max(todayHigh, candle.getHigh());
        todayLow = Math.min(todayLow, candle.getLow());

        switch (phase) {
            case WAIT_BREAKOUT -> waitForBreakout(candle, time);
            case IN_TRADE -> manageTrade(candle, time);
            case DONE -> { }
        }
        previous = candle;
    }

    private void startDay(LocalDate newDay, CandleInfo first) {
        if (day != null) {
            pdh = todayHigh;
            pdl = todayLow;
        }
        day = newDay;
        todayHigh = -Double.MAX_VALUE;
        todayLow = Double.MAX_VALUE;
        pendingEntry = null;
        trade = null;

        boolean noLevels = Double.isNaN(pdh);
        boolean gapped = !noLevels && (first.getOpen() > pdh || first.getOpen() < pdl);
        phase = (noLevels || gapped) ? Phase.DONE : Phase.WAIT_BREAKOUT;
    }

    private void waitForBreakout(CandleInfo candle, LocalTime time) {
        if (pendingEntry != null) {
            enter(candle, time);
            return;
        }
        if (!time.isBefore(LAST_SIGNAL)) {
            phase = Phase.DONE;
            return;
        }
        if (candle.getClose() > pdh) {
            pendingEntry = Direction.LONG;
            signalStop = candle.getLow();
        } else if (candle.getClose() < pdl) {
            pendingEntry = Direction.SHORT;
            signalStop = candle.getHigh();
        }
    }

    private void enter(CandleInfo candle, LocalTime time) {
        Direction direction = pendingEntry;
        pendingEntry = null;
        double entry = candle.getOpen();
        boolean isLong = direction == Direction.LONG;

        double risk = isLong ? entry - signalStop : signalStop - entry;
        double minRisk = entry * MIN_RISK_PERCENT / 100;
        double maxRisk = entry * MAX_RISK_PERCENT / 100;
        if (risk > maxRisk) {
            phase = Phase.DONE;   // entry too far from the level (huge signal candle or a gap): don't chase
            return;
        }
        risk = Math.max(risk, minRisk);   // also covers risk <= 0 when the open gapped back through the signal low/high

        double stop = isLong ? entry - risk : entry + risk;
        double target = isLong ? entry + REWARD_TO_RISK * risk : entry - REWARD_TO_RISK * risk;

        trade = new Trade(day, direction, pdh, pdl, candle.getTime(), entry, stop, target);
        phase = Phase.IN_TRADE;
        manageTrade(candle, time);
    }

    private void manageTrade(CandleInfo candle, LocalTime time) {
        if (!time.isBefore(SQUARE_OFF)) {
            exit(candle, candle.getOpen(), "SQUARE_OFF");
            return;
        }
        boolean isLong = trade.getDirection() == Direction.LONG;
        double stop = trade.getStopLoss();
        double target = trade.getTarget();

        boolean stopHit = isLong ? candle.getLow() <= stop : candle.getHigh() >= stop;
        if (stopHit) {
            exit(candle, isLong ? Math.min(stop, candle.getOpen()) : Math.max(stop, candle.getOpen()), "STOP_LOSS");
            return;
        }
        boolean targetHit = isLong ? candle.getHigh() >= target : candle.getLow() <= target;
        if (targetHit) {
            exit(candle, isLong ? Math.max(target, candle.getOpen()) : Math.min(target, candle.getOpen()), "TARGET");
        }
    }

    private void exit(CandleInfo candle, double price, String reason) {
        trade.close(candle.getTime(), price, reason);
        trades.add(trade);
        trade = null;
        phase = Phase.DONE;
    }

    private void closeAtDayEnd() {
        if (trade != null) {
            exit(previous, previous.getClose(), "DAY_END");
        }
    }
}
