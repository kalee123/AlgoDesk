package com.app.algodesk.models;

import java.util.Optional;

// The strategies the app knows about. The code is what URLs use (/backtest/orb, POST /api/strategies/orb/backtest)
// and what StrategyBackTester maps to a Java class; name and description are only for display.
// An enum rather than a table: a strategy is a Java class today, so the list of them belongs in code.
// It moves to a DB table when strategies become data (the rule builder, per-strategy parameters).
public enum StrategyCode {

    ORB("orb", "Opening Range Breakout",
            "Range 09:15–09:30 · enter on the first close outside it · stop = other side of the range · "
                    + "target 2× risk · square off 15:15 · one trade a day."),
    PDB("pdb", "Previous Day High/Low Breakout",
            "Levels = previous day's high and low · enter on the first close beyond one · stop = signal candle's "
                    + "low/high (min 0.3%, skip above 1.5%) · target 2× risk · square off 15:15 · gap days skipped.");

    private final String code;
    private final String name;
    private final String description;

    StrategyCode(String code, String name, String description) {
        this.code = code;
        this.name = name;
        this.description = description;
    }

    public String getCode() { return code; }
    public String getName() { return name; }
    public String getDescription() { return description; }

    // "orb" / "ORB" -> ORB; empty for anything unknown so the caller decides the error
    public static Optional<StrategyCode> fromCode(String code) {
        for (StrategyCode s : values()) {
            if (s.code.equalsIgnoreCase(code)) {
                return Optional.of(s);
            }
        }
        return Optional.empty();
    }
}
