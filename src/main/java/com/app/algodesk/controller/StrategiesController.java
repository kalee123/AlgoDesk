package com.app.algodesk.controller;

import com.app.algodesk.backtest.StrategyFactory;
import com.app.algodesk.models.BacktestResult;
import com.app.algodesk.models.HistoricalDataRequest;
import com.app.algodesk.service.StrategyService;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping(value = "/api/strategies", produces = MediaType.APPLICATION_JSON_VALUE)
public class StrategiesController {

    private final StrategyFactory strategyFactory;
    private final StrategyService strategyService;

    public StrategiesController(StrategyFactory strategyFactory, StrategyService strategyService) {
        this.strategyFactory = strategyFactory;
        this.strategyService = strategyService;
    }

    // GET /api/strategies -> [{code, name, description}, ...]; fills the dropdown on the Backtest page.
    @GetMapping
    public String list() {
        JSONArray out = new JSONArray();
        strategyFactory.all().forEach((code, strategy) -> out.put(new JSONObject()
                .put("code", code)
                .put("name", strategy.name())
                .put("description", strategy.description())));
        return out.toString();
    }

    // POST /api/strategies/orb/backtest with a JSON body
    // {"exchange":"NSE","symbol":"RELIANCE","interval":"5minute","from":"2025-01-01","to":"2025-03-31"}
    // -> {candles: [...], trades: [...], summary: {...}}
    @PostMapping("/{code}/backtest")
    public String backtest(@PathVariable String code, @RequestBody HistoricalDataRequest historicalData) throws Exception {
        BacktestResult result = strategyService.runStrategy(historicalData, code);
        return new JSONObject(result).toString();
    }
}
