package com.app.algodesk.backtest;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;
import java.util.TreeMap;

// Looks up strategies by code. Spring builds the map from every TradingStrategy bean, keyed by bean name,
// so @Component("orb") is what makes "orb" a valid code. Nothing to edit here when a strategy is added.
@Component
public class StrategyFactory {

    private final Map<String, TradingStrategy> strategies;

    public StrategyFactory(Map<String, TradingStrategy> strategies) {
        this.strategies = new TreeMap<>(strategies);   // sorted by code, so listings are stable
    }

    public TradingStrategy getStrategy(String code) {
        TradingStrategy strategy = strategies.get(code.toLowerCase());
        if (strategy == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown strategy '" + code + "'. Use one of " + strategies.keySet());
        }
        return strategy;
    }

    // code -> strategy, for the list on the Backtest page
    public Map<String, TradingStrategy> all() {
        return strategies;
    }
}
