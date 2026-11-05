package com.app.algodesk.backtest;

import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.Trade;
import com.app.algodesk.models.Trade.Direction;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class BacktestSummaryTest {

    private static Trade trade(Direction direction, double entry, double exit, String reason) {
        Trade t = new Trade(LocalDate.of(2025, 3, 12), direction, 105, 98, 1000, entry, 98, 122);
        t.close(2000, exit, reason);
        return t;
    }

    @Test
    void computesWinsLossesAndRatios() {
        // pnl: +16, -8, +4 (short 97 -> 93), -8  => total +4, equity path 16, 8, 12, 4 -> peak 16, trough 4 => dd 12
        List<Trade> trades = List.of(
                trade(Direction.LONG, 106, 122, "TARGET"),
                trade(Direction.LONG, 106, 98, "STOP_LOSS"),
                trade(Direction.SHORT, 97, 93, "SQUARE_OFF"),
                trade(Direction.LONG, 106, 98, "STOP_LOSS"));

        BacktestSummary s = new BacktestSummary(trades);

        assertEquals(4, s.getTrades());
        assertEquals(2, s.getWins());
        assertEquals(2, s.getLosses());
        assertEquals(50.0, s.getWinRate());
        assertEquals(4.0, s.getTotalPnl(), 1e-9);
        assertEquals(10.0, s.getAvgWin());
        assertEquals(-8.0, s.getAvgLoss());
        assertEquals(20.0 / 16.0, s.getProfitFactor(), 1e-9);
        assertEquals(12.0, s.getMaxDrawdown(), 1e-9);
        assertEquals(16.0, s.getBestTrade());
        assertEquals(-8.0, s.getWorstTrade());
        assertEquals(2, s.getExitReasons().get("STOP_LOSS"));
        assertEquals(1, s.getExitReasons().get("TARGET"));
    }

    @Test
    void noLossesMeansNoProfitFactor() {
        BacktestSummary s = new BacktestSummary(List.of(trade(Direction.LONG, 106, 122, "TARGET")));
        assertNull(s.getProfitFactor());
        assertEquals(100.0, s.getWinRate());
    }

    @Test
    void emptyRunIsAllZeros() {
        BacktestSummary s = new BacktestSummary(List.of());
        assertEquals(0, s.getTrades());
        assertEquals(0.0, s.getTotalPnl());
        assertEquals(0.0, s.getMaxDrawdown());
    }

    // The controller serialises the result with org.json's bean reading; make sure nested objects come out right
    @Test
    void resultSerialisesToTheShapeThePageExpects() {
        List<CandleInfo> candles = List.of(new CandleInfo(1000, 100, 105, 98, 102, 500));
        BacktestResult result = new BacktestResult(candles, List.of(trade(Direction.LONG, 106, 122, "TARGET")));

        // Round-trip through the string, as the controller does: enums and dates only become text when written
        JSONObject json = new JSONObject(new JSONObject(result).toString());

        assertEquals(102.0, json.getJSONArray("candles").getJSONObject(0).getDouble("close"));
        JSONObject t = json.getJSONArray("trades").getJSONObject(0);
        assertEquals("LONG", t.getString("direction"));
        assertEquals("2025-03-12", t.getString("date"));
        assertEquals(16.0, t.getDouble("pnl"));
        assertEquals(1, json.getJSONObject("summary").getInt("wins"));
        assertEquals(1, json.getJSONObject("summary").getJSONObject("exitReasons").getInt("TARGET"));
    }
}
