package com.app.algodesk.backtest;

import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.Trade;
import com.app.algodesk.models.Trade.Direction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Hand-made 5-minute candles. Every test uses the same opening range:
// 09:15-09:25 -> range high 105, range low 98.
class OpeningRangeBreakoutTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate DAY = LocalDate.of(2025, 3, 12);

    private static CandleInfo candle(LocalDate day, String time, double open, double high, double low, double close) {
        long epoch = day.atTime(LocalTime.parse(time)).atZone(IST).toEpochSecond();
        return new CandleInfo(epoch, open, high, low, close, 1000);
    }

    private static CandleInfo candle(String time, double open, double high, double low, double close) {
        return candle(DAY, time, open, high, low, close);
    }

    private static List<CandleInfo> openingRange(LocalDate day) {
        List<CandleInfo> candles = new ArrayList<>();
        candles.add(candle(day, "09:15", 100, 105, 98, 102));
        candles.add(candle(day, "09:20", 102, 104, 99, 103));
        candles.add(candle(day, "09:25", 103, 104, 100, 101));
        return candles;
    }

    // Long breakout: 09:30 closes at 106 > 105 -> enter 09:35 open 106, stop 98 (risk 8), target 122
    private static List<CandleInfo> longEntry(LocalDate day) {
        List<CandleInfo> candles = openingRange(day);
        candles.add(candle(day, "09:30", 101, 106, 101, 106));
        candles.add(candle(day, "09:35", 106, 110, 104, 109));
        return candles;
    }

    @Test
    void longBreakoutHitsTarget() {
        List<CandleInfo> candles = longEntry(DAY);
        candles.add(candle("09:40", 109, 123, 108, 122));
        candles.add(candle("09:45", 122, 131, 121, 130));   // another breakout later: still one trade a day

        List<Trade> trades = OpeningRangeBreakout.run(candles);

        assertEquals(1, trades.size());
        Trade t = trades.get(0);
        assertEquals(Direction.LONG, t.getDirection());
        assertEquals(105, t.getRangeHigh());
        assertEquals(98, t.getRangeLow());
        assertEquals(106, t.getEntryPrice());
        assertEquals(98, t.getStopLoss());
        assertEquals(122, t.getTarget());
        assertEquals("TARGET", t.getExitReason());
        assertEquals(122, t.getExitPrice());
        assertEquals(16, t.getPnl());
    }

    @Test
    void shortBreakoutHitsStop() {
        // 09:30 closes at 97 < 98 -> short at 09:35 open 97, stop 105, target 97 - 2 x 8 = 81
        List<CandleInfo> candles = openingRange(DAY);
        candles.add(candle("09:30", 101, 101, 96, 97));
        candles.add(candle("09:35", 97, 99, 95, 96));
        candles.add(candle("09:40", 96, 106, 96, 104));

        Trade t = OpeningRangeBreakout.run(candles).get(0);

        assertEquals(Direction.SHORT, t.getDirection());
        assertEquals(97, t.getEntryPrice());
        assertEquals(81, t.getTarget());
        assertEquals("STOP_LOSS", t.getExitReason());
        assertEquals(105, t.getExitPrice());
        assertEquals(-8, t.getPnl());
    }

    @Test
    void stopAndTargetInSameCandleAssumesStop() {
        List<CandleInfo> candles = longEntry(DAY);
        candles.add(candle("09:40", 109, 123, 97, 110));

        Trade t = OpeningRangeBreakout.run(candles).get(0);

        assertEquals("STOP_LOSS", t.getExitReason());
        assertEquals(-8, t.getPnl());
    }

    @Test
    void gapThroughStopFillsAtTheOpen() {
        List<CandleInfo> candles = longEntry(DAY);
        candles.add(candle("09:40", 95, 96, 94, 95));

        Trade t = OpeningRangeBreakout.run(candles).get(0);

        assertEquals(95, t.getExitPrice());
        assertEquals(-11, t.getPnl());
    }

    @Test
    void squaresOffAt1515() {
        List<CandleInfo> candles = longEntry(DAY);
        candles.add(candle("09:40", 106, 108, 104, 107));
        candles.add(candle("15:15", 110, 111, 109, 110));

        Trade t = OpeningRangeBreakout.run(candles).get(0);

        assertEquals("SQUARE_OFF", t.getExitReason());
        assertEquals(110, t.getExitPrice());
        assertEquals(4, t.getPnl());
    }

    @Test
    void closesAtLastCandleWhenDataEndsInATrade() {
        List<CandleInfo> candles = longEntry(DAY);
        candles.add(candle("09:40", 109, 112, 107, 111));

        Trade t = OpeningRangeBreakout.run(candles).get(0);

        assertEquals("DAY_END", t.getExitReason());
        assertEquals(111, t.getExitPrice());
    }

    @Test
    void noBreakoutNoTrade() {
        List<CandleInfo> candles = openingRange(DAY);
        candles.add(candle("09:30", 101, 104, 99, 102));
        candles.add(candle("15:20", 102, 104, 101, 103));

        assertTrue(OpeningRangeBreakout.run(candles).isEmpty());
    }

    @Test
    void eachDayGetsItsOwnRangeAndTrade() {
        LocalDate nextDay = DAY.plusDays(1);
        List<CandleInfo> candles = longEntry(DAY);
        candles.add(candle("09:40", 109, 123, 108, 122));
        candles.addAll(longEntry(nextDay));
        candles.add(candle(nextDay, "09:40", 109, 123, 108, 122));

        List<Trade> trades = OpeningRangeBreakout.run(candles);

        assertEquals(2, trades.size());
        assertEquals(DAY, trades.get(0).getDate());
        assertEquals(nextDay, trades.get(1).getDate());
    }
}
