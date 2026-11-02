package com.app.algodesk.controller;

import com.app.algodesk.service.InstrumentSearchService;
import org.json.JSONObject;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/instruments", produces = MediaType.APPLICATION_JSON_VALUE)
public class InstrumentController {

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
}
