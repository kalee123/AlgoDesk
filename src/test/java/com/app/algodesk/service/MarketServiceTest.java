package com.app.algodesk.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class MarketServiceTest {

    @Test
    void changeIsMeasuredFromPreviousClose() {
        // Shape of Kite's /quote/ohlc data; ohlc.close is yesterday's close
        JSONObject quotes = new JSONObject("""
                {
                  "NSE:NIFTY 50":   {"last_price": 23810.85, "ohlc": {"open": 23582, "high": 23850, "low": 23480, "close": 23440.8}},
                  "NSE:NIFTY BANK": {"last_price": 51000,    "ohlc": {"open": 51500, "high": 51600, "low": 50900, "close": 51500}},
                  "BSE:SENSEX":     {"last_price": 78000,    "ohlc": {"open": 0, "high": 0, "low": 0, "close": 0}}
                }""");

        JSONArray indices = MarketService.toIndices(quotes);

        assertEquals(3, indices.length());
        JSONObject nifty = indices.getJSONObject(0);
        assertEquals("NIFTY 50", nifty.getString("label"));
        assertEquals(0, new BigDecimal("370.05").compareTo(nifty.getBigDecimal("change")));
        assertEquals(0, new BigDecimal("1.58").compareTo(nifty.getBigDecimal("changePercent")));   // 370.05 / 23440.8

        JSONObject bank = indices.getJSONObject(1);
        assertEquals("BANK NIFTY", bank.getString("label"));
        assertEquals(0, new BigDecimal("-0.97").compareTo(bank.getBigDecimal("changePercent")));

        assertTrue(indices.getJSONObject(2).isNull("changePercent"), "no previous close -> no percentage");
    }

    @Test
    void missingQuoteIsSkipped() {
        JSONObject quotes = new JSONObject("""
                {"NSE:NIFTY 50": {"last_price": 100, "ohlc": {"close": 100}}}""");

        assertEquals(1, MarketService.toIndices(quotes).length());
    }
}
