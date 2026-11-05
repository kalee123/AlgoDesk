package com.app.algodesk.controller;

import com.app.algodesk.models.HistoricalDataRequest;
import com.app.algodesk.service.StrategyBackTester;
import org.json.JSONObject;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

// Backtests only read candles and compute, nothing is saved, so GET with query params:
// the same URL always gives the same answer and the page can keep it in its address bar.
@RestController
@RequestMapping(value = "/api/backtests", produces = MediaType.APPLICATION_JSON_VALUE)
public class StrategiesController {

    private final StrategyBackTester strategyBackTester;

    public StrategiesController(StrategyBackTester strategyBackTester) {
        this.strategyBackTester = strategyBackTester;
    }

    // e.g. /api/backtests/orb?exchange=NSE&symbol=RELIANCE&interval=5minute&from=2025-01-01&to=2025-03-31
    // -> {candles: [...], trades: [...], summary: {...}}
    @GetMapping("/{strategy}")
    public String backtest(
            @PathVariable String strategy,
            @RequestParam String exchange,
            @RequestParam String symbol,
            @RequestParam(defaultValue = "5minute") String interval,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws Exception {
        HistoricalDataRequest request = new HistoricalDataRequest(exchange, symbol, interval,
                from.atStartOfDay(), to.atTime(23, 59, 59));
        // org.json reads the getters of BacktestResult, its lists and the summary
        return new JSONObject(strategyBackTester.backTest(request, strategy)).toString();
    }
}
