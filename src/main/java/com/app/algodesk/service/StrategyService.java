package com.app.algodesk.service;

import com.app.algodesk.backtest.StrategyFactory;
import com.app.algodesk.backtest.TradingStrategy;
import com.app.algodesk.models.BacktestResult;
import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.HistoricalDataRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Service
public class StrategyService {

    private final CandleService candleService;
    private final StrategyFactory strategyFactory;

    public StrategyService(CandleService candleService, StrategyFactory strategyFactory) {
        this.candleService = candleService;
        this.strategyFactory = strategyFactory;
    }

    public BacktestResult runStrategy(HistoricalDataRequest request, String code) throws Exception {
        TradingStrategy strategy = strategyFactory.getStrategy(code);   // fail fast, before calling Kite
        if ("day".equals(request.getInterval())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Intraday strategies need intraday candles, e.g. 5minute");
        }
        List<CandleInfo> candles = candleService.getHistoricCandles(request);
        return new BacktestResult(candles, strategy.run(candles));
    }
}
