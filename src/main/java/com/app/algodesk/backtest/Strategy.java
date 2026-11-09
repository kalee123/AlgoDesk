package com.app.algodesk.backtest;

import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.Signal;
import com.app.algodesk.models.Trade;
import com.app.algodesk.models.Trade.Direction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

// The backtest engine, as a Template Method: this class owns the candle loop and everything about
// managing an open trade (fill at next open, stop before target, gap fills, square-off, day end).
// A concrete strategy only decides WHEN to enter and WHERE the stop goes, by filling the three holes
// onNewDay / onCandle / entrySignal. One instance per run; it holds the state.
//
//   for each candle (oldest first):
//       day changed?   -> close a leftover trade, reset, onNewDay(...)  (false = skip this day)
//       onCandle(...)  -> always, so levels keep building even while in a trade or done for the day
//       in a trade     -> manageTrade
//       signal pending -> enter at this candle's open
//       otherwise      -> entrySignal(...) until lastEntryTime
public abstract class Strategy {

    protected static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    private final List<Trade> trades = new ArrayList<>();
    private LocalDate day;
    private boolean dayDone;
    private int tradesToday;
    private Signal pending;        // signal seen on the previous candle; fill at this candle's open
    private Trade trade;           // the open trade, if any
    private CandleInfo previous;

    // ---------- the holes a strategy fills ----------

    // First candle of a new day, before onCandle. Reset per-day state here.
    // Return false to skip the whole day (no levels yet, gap open, holiday half-day...).
    protected abstract boolean onNewDay(LocalDate day, CandleInfo firstCandle);

    // Every candle, before any trading decision. Build levels / indicators here.
    protected abstract void onCandle(CandleInfo candle, LocalTime time);

    // Only asked when there's no open trade, nothing pending, the day isn't done and time < lastEntryTime.
    // Return null for "nothing to do".
    protected abstract Signal entrySignal(CandleInfo candle, LocalTime time);

    // ---------- knobs with defaults; override to change ----------

    protected LocalTime lastEntryTime() { return LocalTime.of(15, 0); }
    protected LocalTime squareOffTime() { return LocalTime.of(15, 15); }
    protected double rewardToRisk() { return 2.0; }
    protected int maxTradesPerDay() { return 1; }
    // Risk (entry to stop) as % of entry. Below min: stop is widened to min. Above max: no trade.
    protected double minRiskPercent() { return 0; }
    protected double maxRiskPercent() { return Double.MAX_VALUE; }

    // ---------- the engine ----------

    // candles must be oldest first (as Kite returns them)
    public final List<Trade> run(List<CandleInfo> candles) {
        for (CandleInfo candle : candles) {
            ZonedDateTime t = Instant.ofEpochSecond(candle.getTime()).atZone(IST);
            LocalTime time = t.toLocalTime();

            if (!t.toLocalDate().equals(day)) {
                closeAtDayEnd();
                startDay(t.toLocalDate(), candle);
            }
            onCandle(candle, time);

            if (trade != null) {
                manageTrade(candle, time);
            } else if (pending != null) {
                enter(candle, time);
            } else if (!dayDone) {
                if (time.isBefore(lastEntryTime())) {
                    pending = entrySignal(candle, time);
                } else {
                    dayDone = true;
                }
            }
            previous = candle;
        }
        closeAtDayEnd();
        return trades;
    }

    private void startDay(LocalDate newDay, CandleInfo first) {
        day = newDay;
        tradesToday = 0;
        pending = null;
        trade = null;
        dayDone = !onNewDay(newDay, first);
    }

    private void enter(CandleInfo candle, LocalTime time) {
        Signal signal = pending;
        pending = null;
        boolean isLong = signal.direction() == Direction.LONG;
        double entry = candle.getOpen();

        double risk = isLong ? entry - signal.stop() : signal.stop() - entry;
        if (risk > entry * maxRiskPercent() / 100) {
            dayDone = true;   // entry too far from the level: don't chase
            return;
        }
        risk = Math.max(risk, entry * minRiskPercent() / 100);
        if (risk <= 0) {
            dayDone = true;   // opened on the wrong side of the stop (gap back through the level)
            return;
        }
        double stop = isLong ? entry - risk : entry + risk;
        double target = isLong ? entry + rewardToRisk() * risk : entry - rewardToRisk() * risk;

        trade = new Trade(day, signal.direction(), signal.levelHigh(), signal.levelLow(),
                candle.getTime(), entry, stop, target);
        manageTrade(candle, time);   // the entry candle itself can already hit the stop or target
    }

    private void manageTrade(CandleInfo candle, LocalTime time) {
        if (!time.isBefore(squareOffTime())) {
            exit(candle, candle.getOpen(), "SQUARE_OFF");
            return;
        }
        boolean isLong = trade.getDirection() == Direction.LONG;
        double stop = trade.getStopLoss();
        double target = trade.getTarget();

        // Stop before target: within one candle we can't know which came first, so assume the worse
        boolean stopHit = isLong ? candle.getLow() <= stop : candle.getHigh() >= stop;
        if (stopHit) {
            // A gap through the stop fills at the open, which is worse than the stop price
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
        tradesToday++;
        if (tradesToday >= maxTradesPerDay()) {
            dayDone = true;
        }
    }

    // The day's data ended (half day, missing candles) while still in a trade: close at the last close
    private void closeAtDayEnd() {
        if (trade != null) {
            exit(previous, previous.getClose(), "DAY_END");
        }
    }
}
