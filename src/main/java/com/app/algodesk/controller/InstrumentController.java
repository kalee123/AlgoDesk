package com.app.algodesk.controller;

import com.app.algodesk.service.CandleService;
import com.app.algodesk.service.InstrumentSearchService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

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

    // e.g. /api/instruments/NSE/RELIANCE/candles?interval=5minute
    @GetMapping("/{exchange}/{symbol}/candles")
    public String candles(
            @PathVariable String exchange,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "day") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) throws Exception {
        return candleService.getHistoricCandles(exchange, symbol, interval, from, to);
    }
}
