package com.app.algodesk.models;

import com.app.algodesk.models.Trade.Direction;

// What a strategy answers when it wants to trade: which way, where the raw stop belongs,
// and the two levels the signal came from (shown on the chart / in the trade table).
// The engine fills the order at the next candle's open and turns stop + rewardToRisk into a target.
public record Signal(Direction direction, double stop, double levelHigh, double levelLow) {

    public static Signal longAbove(double stop, double levelHigh, double levelLow) {
        return new Signal(Direction.LONG, stop, levelHigh, levelLow);
    }

    public static Signal shortBelow(double stop, double levelHigh, double levelLow) {
        return new Signal(Direction.SHORT, stop, levelHigh, levelLow);
    }
}
