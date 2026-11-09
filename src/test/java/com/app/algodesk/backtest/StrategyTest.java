package com.app.algodesk.backtest;

import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.Signal;
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

// Tests the engine on its own with a deliberately dumb strategy: "go long at the 09:20 candle, stop 10 below".
// Real strategies get their own tests; these only cover what Strategy itself does.
class StrategyTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate DAY = LocalDate.of(2025, 3, 12);

    // Signals a long on the 09:20 close with the stop 10 points below it
    static class LongAt0920 extends Strategy {
        boolean tradeToday = true;
        double minRisk = 0;
        double maxRisk = Double.MAX_VALUE;

        @Override public String name() { return "Long at 09:20"; }
        @Override public String description() { return "test only"; }
        @Override protected boolean onNewDay(LocalDate day, CandleInfo first) { return tradeToday; }
        @Override protected void onCandle(CandleInfo candle, LocalTime time) { }
        @Override protected Signal entrySignal(CandleInfo candle, LocalTime time) {
            return time.equals(LocalTime.of(9, 20)) ? Signal.longAbove(candle.getClose() - 10, 0, 0) : null;
        }
        @Override protected double minRiskPercent() { return minRisk; }
        @Override protected double maxRiskPercent() { return maxRisk; }
    }

    private static CandleInfo candle(String time, double open, double high, double low, double close) {
        long epoch = DAY.atTime(LocalTime.parse(time)).atZone(IST).toEpochSecond();
        return new CandleInfo(epoch, open, high, low, close, 1000);
    }

    // 09:20 closes at 100 -> signal, stop 90. 09:25 opens at 102 -> entry 102, risk 12, target 126
    private static List<CandleInfo> signalThenEntry() {
        List<CandleInfo> c = new ArrayList<>();
        c.add(candle("09:15", 98, 101, 97, 99));
        c.add(candle("09:20", 99, 101, 98, 100));
        c.add(candle("09:25", 102, 104, 101, 103));
        return c;
    }

    @Test
    void fillsAtNextOpenAndBuildsTargetFromRisk() {
        List<CandleInfo> c = signalThenEntry();
        c.add(candle("09:30", 103, 127, 102, 126));

        Trade t = new LongAt0920().run(c).get(0);

        assertEquals(Direction.LONG, t.getDirection());
        assertEquals(102, t.getEntryPrice());
        assertEquals(90, t.getStopLoss());
        assertEquals(126, t.getTarget(), 1e-9);
        assertEquals("TARGET", t.getExitReason());
        assertEquals(24, t.getPnl(), 1e-9);
    }

    @Test
    void stopBeforeTargetInTheSameCandle() {
        List<CandleInfo> c = signalThenEntry();
        c.add(candle("09:30", 103, 130, 89, 120));

        Trade t = new LongAt0920().run(c).get(0);

        assertEquals("STOP_LOSS", t.getExitReason());
        assertEquals(90, t.getExitPrice());
    }

    @Test
    void gapThroughStopFillsAtOpen() {
        List<CandleInfo> c = signalThenEntry();
        c.add(candle("09:30", 85, 86, 84, 85));

        assertEquals(85, new LongAt0920().run(c).get(0).getExitPrice());
    }

    @Test
    void squaresOffAtSquareOffTime() {
        List<CandleInfo> c = signalThenEntry();
        c.add(candle("15:15", 110, 111, 109, 110));

        Trade t = new LongAt0920().run(c).get(0);

        assertEquals("SQUARE_OFF", t.getExitReason());
        assertEquals(110, t.getExitPrice());
    }

    @Test
    void dataEndingMidTradeClosesAtLastClose() {
        Trade t = new LongAt0920().run(signalThenEntry()).get(0);

        assertEquals("DAY_END", t.getExitReason());
        assertEquals(103, t.getExitPrice());
    }

    @Test
    void onNewDayFalseSkipsTheDay() {
        LongAt0920 s = new LongAt0920();
        s.tradeToday = false;

        assertTrue(s.run(signalThenEntry()).isEmpty());
    }

    @Test
    void minRiskWidensTheStop() {
        LongAt0920 s = new LongAt0920();
        s.minRisk = 20;   // 20% of 102 = 20.4 > the 12 risk from the signal

        Trade t = s.run(signalThenEntry()).get(0);

        assertEquals(102 - 20.4, t.getStopLoss(), 1e-9);
        assertEquals(102 + 40.8, t.getTarget(), 1e-9);
    }

    @Test
    void maxRiskSkipsTheTrade() {
        LongAt0920 s = new LongAt0920();
        s.maxRisk = 5;    // 5% of 102 = 5.1 < the 12 risk

        assertTrue(s.run(signalThenEntry()).isEmpty());
    }

    @Test
    void openBeyondTheStopSkipsTheTrade() {
        List<CandleInfo> c = new ArrayList<>();
        c.add(candle("09:15", 98, 101, 97, 99));
        c.add(candle("09:20", 99, 101, 98, 100));    // stop 90
        c.add(candle("09:25", 85, 86, 84, 85));      // opens below the stop

        assertTrue(new LongAt0920().run(c).isEmpty());
    }

    @Test
    void oneTradePerDayByDefault() {
        List<CandleInfo> c = signalThenEntry();
        c.add(candle("09:30", 103, 127, 102, 126));   // target
        c.add(candle("09:35", 126, 130, 125, 129));   // would be ignored even if a signal fired

        assertEquals(1, new LongAt0920().run(c).size());
    }
}
