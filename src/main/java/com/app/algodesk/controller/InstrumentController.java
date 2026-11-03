package com.app.algodesk.controller;

import com.app.algodesk.service.CandleService;
import com.app.algodesk.service.InstrumentSearchService;
import org.json.JSONObject;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@RestController
@RequestMapping(value = "/api/instruments", produces = MediaType.APPLICATION_JSON_VALUE)
public class InstrumentController {

    @Autowired
    CandleService candleService;

    private final InstrumentSearchService instrumentSearchService;

    public InstrumentController(InstrumentSearchService instrumentSearchService) {
        this.instrumentSearchService = instrumentSearchService;
    }

    // e.g. /api/instruments/search?q=reli
    @GetMapping("/search")
    public ResponseEntity<String> search(@RequestParam(name = "q", defaultValue = "") String q) {
        try {
            return ResponseEntity.ok(instrumentSearchService.search(q));
        } catch (Exception e) {
            return ResponseEntity.status(500).body(new JSONObject().put("error", "Search failed").toString());
        }
    }

    @GetMapping("/{exchange}/{symbol}/candles")
    public ResponseEntity<String> candles(
            @PathVariable String exchange,
            @PathVariable String symbol,
            @RequestParam(defaultValue = "day") String interval,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to){
        try {
            return ResponseEntity.ok(candleService.getHistoricCandles(exchange, symbol, interval, from, to));
        } catch (ResponseStatusException e) {
            // Caller's mistake (bad interval, bad range, unknown instrument): 400 / 404 with the reason
            return ResponseEntity.status(e.getStatusCode()).body(new JSONObject().put("error", e.getReason()).toString());
        } catch (Exception e) {
            // Kite call failed
            return ResponseEntity.status(502).body(new JSONObject().put("error", "Could not load candles").toString());
        }
    }
}
