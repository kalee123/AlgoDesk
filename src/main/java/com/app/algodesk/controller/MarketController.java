package com.app.algodesk.controller;

import com.app.algodesk.service.MarketService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Errors are turned into JSON responses by ApiExceptionHandler
@RestController
@RequestMapping(value = "/api/market", produces = MediaType.APPLICATION_JSON_VALUE)
public class MarketController {

    private final MarketService marketService;

    public MarketController(MarketService marketService) {
        this.marketService = marketService;
    }

    // Separate from /api/dashboard/summary because it changes every second
    // (and later becomes live through the ticker), while the summary only needs a refresh every minute.
    @GetMapping("/indices")
    public String indices() throws Exception {
        return marketService.getIndices();
    }
}
