package com.app.algodesk.backtest;

import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.Trade;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

// My own ORB, built from scratch next to OpeningRangeBreakout. Code "orb2" so both show up until this one
// passes OpeningRangeBreakoutTest; then this becomes "orb" and the other class goes.
@Component("orb2")
public class OpenRangeBreakout implements TradingStrategy {

    @Override
    public String name() {
        return "ORB (mine)";
    }

    @Override
    public String description() {
        return "Work in progress";
    }

    @Override
    public List<Trade> run(List<CandleInfo> candles) {
        return new ArrayList<>();
    }
}
