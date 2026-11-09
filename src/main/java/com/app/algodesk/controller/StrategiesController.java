package com.app.algodesk.controller;

import com.app.algodesk.models.BacktestResult;
import com.app.algodesk.models.HistoricalDataRequest;
import com.app.algodesk.models.StrategyCode;
import com.app.algodesk.service.StrategyBackTester;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

// Strategies are the resource; a backtest is an action on one of them, so it hangs off the strategy's URL.
@RestController
@RequestMapping(value = "/api/strategies", produces = MediaType.APPLICATION_JSON_VALUE)
public class StrategiesController {

    private final StrategyBackTester strategyBackTester;

    public StrategiesController(StrategyBackTester strategyBackTester) {
        this.strategyBackTester = strategyBackTester;
    }

    // GET /api/strategies -> [{code, name, description}, ...]; fills the dropdown on the Backtest page.
    // Strategies are an enum today, so there's nothing to look up: just shape it for the browser.
    @GetMapping
    public String list() {
        JSONArray out = new JSONArray();
        for (StrategyCode s : StrategyCode.values()) {
            out.put(new JSONObject()
                    .put("code", s.getCode())
                    .put("name", s.getName())
                    .put("description", s.getDescription()));
        }
        return out.toString();
    }

    // POST /api/strategies/orb/backtest with a JSON body
    // {"exchange":"NSE","symbol":"RELIANCE","interval":"5minute","from":"2025-01-01","to":"2025-03-31"}
    // -> {candles: [...], trades: [...], summary: {...}}
    @PostMapping("/{code}/backtest")
    public String backtest(@PathVariable String code, @RequestBody HistoricalDataRequest historicalData) throws Exception {
        BacktestResult result = strategyBackTester.backTest(historicalData, code);
        return new JSONObject(result).toString();
    }
}
