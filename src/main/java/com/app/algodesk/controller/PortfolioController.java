package com.app.algodesk.controller;

import com.app.algodesk.service.OrderService;
import com.app.algodesk.service.PortfolioService;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

// Errors are turned into JSON responses by ApiExceptionHandler
@RestController
@RequestMapping(value = "/api", produces = MediaType.APPLICATION_JSON_VALUE)
public class PortfolioController {

    private final PortfolioService portfolioService;
    private final OrderService orderService;

    public PortfolioController(PortfolioService portfolioService, OrderService orderService) {
        this.portfolioService = portfolioService;
        this.orderService = orderService;
    }

    @GetMapping("/orders")
    public String orders() throws Exception {
        return orderService.getOrders();
    }

    @GetMapping("/holdings")
    public String holdings() throws Exception {
        return portfolioService.getHoldings();
    }

    @GetMapping("/positions")
    public String positions() throws Exception {
        return portfolioService.getPositions();
    }
}
