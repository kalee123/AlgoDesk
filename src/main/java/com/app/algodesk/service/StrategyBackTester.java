package com.app.algodesk.service;

import com.app.algodesk.backtest.OpeningRangeBreakout;
import com.app.algodesk.backtest.PreviousDayBreakout;
import com.app.algodesk.models.BacktestResult;
import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.HistoricalDataRequest;
import com.app.algodesk.models.StrategyCode;
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
        StrategyCode code = StrategyCode.fromCode(strategy).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown strategy '" + strategy + "'"));
        if ("day".equals(request.getInterval())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intraday strategies need intraday candles, e.g. 5minute");
        }
        List<CandleInfo> candles = candleService.getHistoricCandles(request);
        // Exhaustive switch over the enum: adding a StrategyCode without a case here is a compile error
        List<Trade> trades = switch (code) {
            case ORB -> OpeningRangeBreakout.run(candles);
            case PDB -> PreviousDayBreakout.run(candles);
        };
        return new BacktestResult(candles, trades);
    }
}
