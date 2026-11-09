package com.app.algodesk.backtest;

import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.Trade;

import java.util.List;

// One tradable strategy. Each implementation is a Spring bean whose bean name is the strategy's code
// (e.g. @Component("orb")); StrategyFactory collects them all, so adding a strategy is just adding a class.
public interface TradingStrategy {

    // Display name for the Backtest page, e.g. "Opening Range Breakout"
    String name();

    // One-line summary of the rules, shown under the strategy picker
    String description();

    // Runs the strategy over candles (oldest first) and returns the closed trades.
    // Beans are singletons, so an implementation must not keep per-run state in its fields across calls.
    List<Trade> run(List<CandleInfo> candles);
}
