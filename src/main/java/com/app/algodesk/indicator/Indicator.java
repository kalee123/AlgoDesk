package com.app.algodesk.indicator;

import com.app.algodesk.models.CandleInfo;

import java.util.List;

// A technical indicator: pure maths over candles, no Spring, no I/O, so it is easy to test.
// T is the shape of the result, because indicators differ:
//   per-candle ones (SMA, EMA, RSI) return one value per candle, e.g. double[]
//   per-day ones (CPR) return levels per date, e.g. Map<LocalDate, CprLevels>
// Strategies (backtest) and the charts (Market Watch) can both use the same implementation.
public interface Indicator<T> {

    // Short display name, e.g. "CPR" or "SMA 20"
    String name();

    // Calculates the indicator over candles (oldest first)
    T calculate(List<CandleInfo> candles);
}
