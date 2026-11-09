package com.app.algodesk.controller;

import org.springframework.core.io.ClassPathResource;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

// Serves the UI pages. Each page loads its data from the matching /api/** endpoint.
@RestController
public class PageController {

    @GetMapping(value = "/dashboard", produces = MediaType.TEXT_HTML_VALUE)
    public Resource dashboard() {
        return page("dashboard");
    }

    @GetMapping(value = "/orders", produces = MediaType.TEXT_HTML_VALUE)
    public Resource orders() {
        return page("orders");
    }

    @GetMapping(value = "/holdings", produces = MediaType.TEXT_HTML_VALUE)
    public Resource holdings() {
        return page("holdings");
    }

    @GetMapping(value = "/positions", produces = MediaType.TEXT_HTML_VALUE)
    public Resource positions() {
        return page("positions");
    }

    @GetMapping(value = "/market-watch", produces = MediaType.TEXT_HTML_VALUE)
    public Resource marketWatch() {
        return page("market-watch");
    }

    // /backtest and /backtest/orb serve the same page; the script reads the strategy from the path
    @GetMapping(value = {"/backtest", "/backtest/{strategy}"}, produces = MediaType.TEXT_HTML_VALUE)
    public Resource backtest() {
        return page("backtest");
    }

    @GetMapping(value = "/logged-out", produces = MediaType.TEXT_HTML_VALUE)
    public Resource loggedOut() {
        return page("logged-out");
    }

    private static Resource page(String name) {
        return new ClassPathResource("static/" + name + ".html");
    }
}
