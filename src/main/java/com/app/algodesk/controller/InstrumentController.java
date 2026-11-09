package com.app.algodesk.controller;

import com.app.algodesk.models.HistoricalDataRequest;
import com.app.algodesk.service.CandleService;
import com.app.algodesk.service.InstrumentSearchService;
import org.json.JSONArray;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

// Errors are turned into JSON responses by ApiExceptionHandler
// (e.g. CandleService throws ResponseStatusException 404 for an unknown instrument)
@RestController
@RequestMapping(value = "/api/instruments", produces = MediaType.APPLICATION_JSON_VALUE)
public class InstrumentController {

    private final InstrumentSearchService instrumentSearchService;
    private final CandleService candleService;

    public InstrumentController(InstrumentSearchService instrumentSearchService, CandleService candleService) {
        this.instrumentSearchService = instrumentSearchService;
        this.candleService = candleService;
    }

    // e.g. /api/instruments/search?q=reli
    @GetMapping("/search")
    public String search(@RequestParam(name = "q", defaultValue = "") String q) {
        return instrumentSearchService.search(q);
    }

    // e.g. /api/instruments/NSE/RELIANCE/candles?interval=5minute&from=2025-01-01&to=2025-03-31
    // Spring fills HistoricalDataRequest from the path variables AND the query parameters by setter name.
    @GetMapping("/{exchange}/{symbol}/candles")
    public String candles(HistoricalDataRequest request) throws Exception {
        // org.json reads the getters: [{time, open, high, low, close, volume}, ...]
        return new JSONArray(candleService.getHistoricCandles(request)).toString();
    }
}
