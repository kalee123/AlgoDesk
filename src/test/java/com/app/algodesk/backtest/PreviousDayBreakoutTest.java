package com.app.algodesk.backtest;

import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.TradeDetails;
import com.app.algodesk.models.TradeDetails.Direction;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Day 1 only sets the levels: high 1100, low 900 (prices around 1000 so 0.3% = 3 and 1.5% = 15).
class PreviousDayBreakoutTest {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final LocalDate DAY1 = LocalDate.of(2025, 3, 11);
    private static final LocalDate DAY2 = DAY1.plusDays(1);
    private static final LocalDate DAY3 = DAY1.plusDays(2);

    private static CandleInfo candle(LocalDate day, String time, double open, double high, double low, double close) {
        long epoch = day.atTime(LocalTime.parse(time)).atZone(IST).toEpochSecond();
        return new CandleInfo(epoch, open, high, low, close, 1000);
    }

    private static List<CandleInfo> dayOne() {
        List<CandleInfo> c = new ArrayList<>();
        c.add(candle(DAY1, "09:15", 1000, 1050, 980, 1020));
        c.add(candle(DAY1, "10:00", 1020, 1100, 1010, 1080));   // day high 1100
        c.add(candle(DAY1, "13:00", 1080, 1090, 900, 950));     // day low 900
        c.add(candle(DAY1, "15:25", 950, 1000, 940, 990));
        return c;
    }

    @Test
    void firstDayOnlyBuildsLevels() {
        assertTrue(new PreviousDayBreakout().run(dayOne()).isEmpty());
    }

    @Test
    void longBreakoutAbovePdhUsesSignalCandleLowAsStop() {
        List<CandleInfo> c = dayOne();
        c.add(candle(DAY2, "09:15", 1000, 1020, 995, 1010));
        c.add(candle(DAY2, "09:20", 1010, 1105, 1095, 1102));   // closes above 1100, low 1095
        c.add(candle(DAY2, "09:25", 1103, 1110, 1100, 1108));   // entry at open 1103: risk 8, target 1115 (1.5x)
        c.add(candle(DAY2, "09:30", 1108, 1120, 1107, 1118));

        TradeDetails t = new PreviousDayBreakout().run(c).get(0);

        assertEquals(Direction.LONG, t.getDirection());
        assertEquals(1100, t.getRangeHigh());
        assertEquals(900, t.getRangeLow());
        assertEquals(1103, t.getEntryPrice());
        assertEquals(1095, t.getStopLoss());
        assertEquals(1115, t.getTarget(), 1e-9);
        assertEquals("TARGET", t.getExitReason());
        assertEquals(12, t.getPnl(), 1e-9);
    }

    @Test
    void shortBreakoutBelowPdlHitsStop() {
        List<CandleInfo> c = dayOne();
        c.add(candle(DAY2, "09:15", 950, 960, 930, 940));
        c.add(candle(DAY2, "09:20", 940, 905, 890, 895));       // closes below 900, high 905
        c.add(candle(DAY2, "09:25", 893, 900, 885, 890));       // entry 893: risk 12, stop 905, target 875 (1.5x)
        c.add(candle(DAY2, "09:30", 890, 910, 889, 908));       // high 910 >= stop 905

        TradeDetails t = new PreviousDayBreakout().run(c).get(0);

        assertEquals(Direction.SHORT, t.getDirection());
        assertEquals(905, t.getStopLoss());
        assertEquals(875, t.getTarget(), 1e-9);
        assertEquals("STOP_LOSS", t.getExitReason());
        assertEquals(-12, t.getPnl(), 1e-9);
    }

    @Test
    void tinySignalCandleGetsMinimumRisk() {
        List<CandleInfo> c = dayOne();
        c.add(candle(DAY2, "09:15", 1000, 1020, 995, 1010));
        c.add(candle(DAY2, "09:20", 1099, 1102, 1098.5, 1101));   // low 1098.5: risk would be 1.5
        c.add(candle(DAY2, "09:25", 1100, 1104, 1099, 1103));     // entry 1100 -> min risk 0.3% = 3.3
        c.add(candle(DAY2, "15:15", 1105, 1106, 1104, 1105));

        TradeDetails t = new PreviousDayBreakout().run(c).get(0);

        assertEquals(1100 - 3.3, t.getStopLoss(), 1e-9);
        assertEquals(1100 + 1.5 * 3.3, t.getTarget(), 1e-9);
    }

    @Test
    void hugeSignalCandleIsSkipped() {
        List<CandleInfo> c = dayOne();
        c.add(candle(DAY2, "09:15", 1000, 1020, 995, 1010));
        c.add(candle(DAY2, "09:20", 1010, 1130, 1005, 1125));     // low 1005: entry 1126 would risk 121 (> 1.5%)
        c.add(candle(DAY2, "09:25", 1126, 1140, 1120, 1138));
        c.add(candle(DAY2, "09:30", 1138, 1160, 1137, 1158));     // would have been a winner; still no trade

        assertTrue(new PreviousDayBreakout().run(c).isEmpty());
    }

    @Test
    void gapOpenAboveLevelSkipsTheDay() {
        List<CandleInfo> c = dayOne();
        c.add(candle(DAY2, "09:15", 1120, 1130, 1115, 1128));     // opened above PDH 1100
        c.add(candle(DAY2, "09:20", 1128, 1140, 1127, 1139));

        assertTrue(new PreviousDayBreakout().run(c).isEmpty());
    }

    @Test
    void dayThreeUsesDayTwoLevelsEvenWhenDayTwoHadNoTrade() {
        List<CandleInfo> c = dayOne();
        // Day 2 stays inside 900-1100, no trade, but makes new levels: high 1060, low 1000
        c.add(candle(DAY2, "09:15", 1020, 1060, 1000, 1040));
        c.add(candle(DAY2, "15:25", 1040, 1050, 1030, 1045));
        // Day 3: 1065 is below day 1's high (1100) but above day 2's (1060)
        c.add(candle(DAY3, "09:15", 1045, 1055, 1040, 1050));
        c.add(candle(DAY3, "09:20", 1050, 1066, 1058, 1065));
        c.add(candle(DAY3, "09:25", 1066, 1070, 1064, 1069));

        List<TradeDetails> trades = new PreviousDayBreakout().run(c);

        assertEquals(1, trades.size());
        assertEquals(DAY3, trades.get(0).getDate());
        assertEquals(1060, trades.get(0).getRangeHigh());
        assertEquals(1000, trades.get(0).getRangeLow());
    }

    @Test
    void candlesAfterDoneStillCountTowardsNextDayLevels() {
        List<CandleInfo> c = dayOne();
        // Day 2: long trade early, then a big spike to 1300 after the trade is done
        c.add(candle(DAY2, "09:15", 1000, 1020, 995, 1010));
        c.add(candle(DAY2, "09:20", 1010, 1105, 1095, 1102));
        c.add(candle(DAY2, "09:25", 1103, 1125, 1100, 1120));     // entry 1103, target 1119 hit here
        c.add(candle(DAY2, "14:00", 1120, 1300, 1118, 1290));
        // Day 3: 1200 is above every level except day 2's real high 1300
        c.add(candle(DAY3, "09:15", 1250, 1260, 1240, 1255));
        c.add(candle(DAY3, "09:20", 1255, 1265, 1250, 1262));

        List<TradeDetails> trades = new PreviousDayBreakout().run(c);

        assertEquals(1, trades.size());   // only day 2's trade; day 3 never broke 1300
        assertEquals(DAY2, trades.get(0).getDate());
    }
}
