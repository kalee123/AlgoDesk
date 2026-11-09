package com.app.algodesk.models;


import java.util.List;

// Everything the backtest page needs in one response: the candles (to draw the chart),
// the trades (markers + table) and the summary (stat tiles).
public class BacktestResult {

    private final List<CandleInfo> candles;
    private final List<Trade> trades;
    private final BacktestSummary summary;

    public BacktestResult(List<CandleInfo> candles, List<Trade> trades) {
        this.candles = candles;
        this.trades = trades;
        this.summary = new BacktestSummary(trades);
    }

    public List<CandleInfo> getCandles() { return candles; }
    public List<Trade> getTrades() { return trades; }
    public BacktestSummary getSummary() { return summary; }
}
