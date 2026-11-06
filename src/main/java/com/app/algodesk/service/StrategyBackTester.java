package com.app.algodesk.service;

import com.app.algodesk.backtest.BacktestResult;
import com.app.algodesk.backtest.OpeningRangeBreakout;
import com.app.algodesk.backtest.PreviousDayBreakout;
import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.HistoricalDataRequest;
import com.app.algodesk.models.Trade;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class StrategyBackTester {

    private final CandleService candleService;

    public StrategyBackTester(CandleService candleService) {
        this.candleService = candleService;
    }

    // Fetches the candles, then runs the strategy over them (the strategy itself does no I/O)
    public BacktestResult backTest(HistoricalDataRequest request, String strategy) throws Exception {
        String key = strategy.toLowerCase();
        if (!key.equals("orb") && !key.equals("pdb")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown strategy '" + strategy + "'. Use orb or pdb");
        }
        if ("day".equals(request.getInterval())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intraday strategies need intraday candles, e.g. 5minute");
        }
        List<CandleInfo> candles = candleService.getHistoricCandles(request);
        List<Trade> trades = switch (key) {
            case "orb" -> OpeningRangeBreakout.run(candles);
            case "pdb" -> PreviousDayBreakout.run(candles);
            default -> throw new IllegalStateException(key);
        };
        return new BacktestResult(candles, trades);
    }
}
