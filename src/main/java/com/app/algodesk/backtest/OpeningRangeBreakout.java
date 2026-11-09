package com.app.algodesk.backtest;

import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.TradeDetails;
import com.app.algodesk.models.TradeDetails.Direction;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

// Opening Range Breakout on intraday candles (built for 5minute), at most one trade per day:
//  1. Range: high/low of the candles before 09:30 (09:15, 09:20, 09:25)
//  2. Signal: first candle that CLOSES above the range high (long) or below the range low (short)
//  3. Entry: the NEXT candle's open, so we never trade on a price we couldn't have known yet
//  4. Stop: the other side of the range. Target: 2 x risk. Square off at 15:15 if neither is hit.
// Pure logic, no I/O: a new instance per run holds the state, so it's easy to unit test.
@Component("orb")
public class OpeningRangeBreakout implements TradingStrategy {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    static final LocalTime RANGE_END = LocalTime.of(9, 30);
    static final LocalTime LAST_SIGNAL = LocalTime.of(15, 0);   // no new breakouts from here
    static final LocalTime SQUARE_OFF = LocalTime.of(15, 15);   // close an open trade at this candle's open
    static final double REWARD_TO_RISK = 2.0;

    // Each day walks through these in order; a new day starts again at BUILD_RANGE
    private enum Phase { BUILD_RANGE, WAIT_BREAKOUT, IN_TRADE, DONE }

    private final List<TradeDetails> trades = new ArrayList<>();
    private LocalDate day;
    private Phase phase;
    private double rangeHigh;
    private double rangeLow;
    private Direction pendingEntry;   // breakout seen on the previous candle; enter at this candle's open
    private TradeDetails trade;              // the open trade, if any
    private CandleInfo previous;

    @Override
    public String name() {
        return "Opening Range Breakout";
    }

    @Override
    public String description() {
        return "Range 09:15–09:30 · enter on the first close outside it · stop = other side of the range · "
                + "target 2× risk · square off 15:15 · one trade a day.";
    }

    // candles must be oldest first (as Kite returns them).
    // The Spring bean is a singleton shared by every request, so each run uses a fresh instance for its state.
    @Override
    public List<TradeDetails> run(List<CandleInfo> candles) {
        OpeningRangeBreakout orb = new OpeningRangeBreakout();
        for (CandleInfo candle : candles) {
            orb.onCandle(candle);
        }
        orb.closeAtDayEnd();
        return orb.trades;
    }

    private void onCandle(CandleInfo candle) {
        ZonedDateTime t = Instant.ofEpochSecond(candle.getTime()).atZone(IST);
        LocalTime time = t.toLocalTime();

        if (!t.toLocalDate().equals(day)) {
            closeAtDayEnd();
            startDay(t.toLocalDate());
        }

        switch (phase) {
            case BUILD_RANGE -> buildRange(candle, time);
            case WAIT_BREAKOUT -> waitForBreakout(candle, time);
            case IN_TRADE -> manageTrade(candle, time);
            case DONE -> { }   // ignore the rest of the day
        }
        previous = candle;
    }

    private void startDay(LocalDate newDay) {
        day = newDay;
        phase = Phase.BUILD_RANGE;
        rangeHigh = -Double.MAX_VALUE;
        rangeLow = Double.MAX_VALUE;
        pendingEntry = null;
        trade = null;
    }

    private void buildRange(CandleInfo candle, LocalTime time) {
        if (time.isBefore(RANGE_END)) {
            rangeHigh = Math.max(rangeHigh, candle.getHigh());
            rangeLow = Math.min(rangeLow, candle.getLow());
            return;
        }
        // First candle after the range. No range candles at all (missing data) -> skip the day.
        if (rangeHigh < rangeLow) {
            phase = Phase.DONE;
            return;
        }
        phase = Phase.WAIT_BREAKOUT;
        waitForBreakout(candle, time);
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
        if (candle.getClose() > rangeHigh) {
            pendingEntry = Direction.LONG;
        } else if (candle.getClose() < rangeLow) {
            pendingEntry = Direction.SHORT;
        }
    }

    private void enter(CandleInfo candle, LocalTime time) {
        Direction direction = pendingEntry;
        pendingEntry = null;
        double entry = candle.getOpen();
        double stop = direction == Direction.LONG ? rangeLow : rangeHigh;

        // Opened on the wrong side of the stop (a big gap back through the range): no trade today
        if (direction == Direction.LONG ? entry <= stop : entry >= stop) {
            phase = Phase.DONE;
            return;
        }
        double risk = Math.abs(entry - stop);
        double target = direction == Direction.LONG ? entry + REWARD_TO_RISK * risk : entry - REWARD_TO_RISK * risk;

        trade = new TradeDetails();
        trade.setDate(day);
        trade.setDirection(direction);
        trade.setRangeHigh(rangeHigh);
        trade.setRangeLow(rangeLow);
        trade.setEntryTime(candle.getTime());
        trade.setEntryPrice(entry);
        trade.setStopLoss(stop);
        trade.setTarget(target);
        phase = Phase.IN_TRADE;
        manageTrade(candle, time);   // the entry candle itself can already hit the stop or target
    }

    private void manageTrade(CandleInfo candle, LocalTime time) {
        if (!time.isBefore(SQUARE_OFF)) {
            exit(candle, candle.getOpen(), "SQUARE_OFF");
            return;
        }
        boolean isLong = trade.getDirection() == Direction.LONG;
        double stop = trade.getStopLoss();
        double target = trade.getTarget();

        // Stop is checked before target: inside one candle we can't tell which came first, so assume the worse
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
        phase = Phase.DONE;
    }

    // The day's data ended (half day, missing candles) while still in a trade: close at the last close
    private void closeAtDayEnd() {
        if (trade != null) {
            exit(previous, previous.getClose(), "DAY_END");
        }
    }
}
