package com.app.algodesk.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

// Pure calculation tests with hand-made Kite-shaped JSON: no Spring, no Kite
class DashBoardServiceTest {

    @Test
    void holdingsOnly() {
        // RELIANCE: 10 shares, 1280 -> 1300 = +200 ; INFY: 5 shares, 1150 -> 1140 = -50
        JSONArray holdings = new JSONArray("""
                [
                  {"tradingsymbol":"RELIANCE","quantity":10,"t1_quantity":0,"last_price":1300,"close_price":1280},
                  {"tradingsymbol":"INFY","quantity":5,"t1_quantity":0,"last_price":1140,"close_price":1150}
                ]""");

        JSONObject pnl = DashBoardService.todayPnl(holdings, new JSONArray());

        assertMoney("150.00", pnl.getBigDecimal("value"));
        assertMoney("150.00", pnl.getBigDecimal("holdings"));
        assertMoney("0.00", pnl.getBigDecimal("positions"));
        // yesterday's value = 12800 + 5750 = 18550 ; 150 / 18550 = 0.81%
        assertMoney("0.81", pnl.getBigDecimal("percent"));
    }

    @Test
    void t1SharesCountTowardsDayChange() {
        JSONArray holdings = new JSONArray("""
                [{"quantity":2,"t1_quantity":3,"last_price":110,"close_price":100}]""");

        assertMoney("50.00", DashBoardService.todayPnl(holdings, new JSONArray()).getBigDecimal("value"));
    }

    @Test
    void positionsUseM2mNotPnl() {
        // Carried future: overall pnl since buy is 5000, but today's move (m2m) is only 1200
        JSONArray positions = new JSONArray("""
                [
                  {"tradingsymbol":"NIFTY26NOVFUT","pnl":5000,"m2m":1200},
                  {"tradingsymbol":"SBIN","pnl":-300,"m2m":-300}
                ]""");

        JSONObject pnl = DashBoardService.todayPnl(new JSONArray(), positions);

        assertMoney("900.00", pnl.getBigDecimal("value"));
        assertMoney("900.00", pnl.getBigDecimal("positions"));
        assertTrue(pnl.isNull("percent"), "no overnight holdings -> no percentage");
    }

    @Test
    void holdingsAndPositionsAddUp() {
        JSONArray holdings = new JSONArray("""
                [{"quantity":10,"t1_quantity":0,"last_price":1300,"close_price":1280}]""");
        JSONArray positions = new JSONArray("""
                [{"m2m":-75.5}]""");

        assertMoney("124.50", DashBoardService.todayPnl(holdings, positions).getBigDecimal("value"));
    }

    @Test
    void holdingWithoutPreviousCloseIsSkipped() {
        JSONArray holdings = new JSONArray("""
                [{"quantity":10,"t1_quantity":0,"last_price":500,"close_price":0}]""");

        JSONObject pnl = DashBoardService.todayPnl(holdings, new JSONArray());

        assertMoney("0.00", pnl.getBigDecimal("value"));
        assertTrue(pnl.isNull("percent"));
    }

    @Test
    void emptyAccount() {
        JSONObject pnl = DashBoardService.todayPnl(new JSONArray(), new JSONArray());

        assertMoney("0.00", pnl.getBigDecimal("value"));
        assertTrue(pnl.isNull("percent"));
    }

    // ---------- Portfolio ----------

    @Test
    void portfolioValueInvestedAndPnl() {
        // RELIANCE: 10 x 1300 now, bought at 1200 ; INFY: 5 x 1140 now, bought at 1500
        JSONArray holdings = new JSONArray("""
                [
                  {"quantity":10,"t1_quantity":0,"last_price":1300,"average_price":1200},
                  {"quantity":5,"t1_quantity":0,"last_price":1140,"average_price":1500}
                ]""");

        JSONObject p = DashBoardService.portfolio(holdings);

        assertMoney("18700.00", p.getBigDecimal("value"));      // 13000 + 5700
        assertMoney("19500.00", p.getBigDecimal("invested"));   // 12000 + 7500
        assertMoney("-800.00", p.getBigDecimal("pnl"));
        assertMoney("-4.10", p.getBigDecimal("pnlPercent"));    // -800 / 19500
    }

    @Test
    void portfolioIncludesT1Shares() {
        JSONArray holdings = new JSONArray("""
                [{"quantity":2,"t1_quantity":3,"last_price":110,"average_price":100}]""");

        assertMoney("550.00", DashBoardService.portfolio(holdings).getBigDecimal("value"));
    }

    @Test
    void emptyPortfolioHasNoPercent() {
        JSONObject p = DashBoardService.portfolio(new JSONArray());

        assertMoney("0.00", p.getBigDecimal("value"));
        assertTrue(p.isNull("pnlPercent"));
    }

    // ---------- Margin ----------

    @Test
    void marginFromEquitySegment() {
        JSONObject margins = new JSONObject("""
                {
                  "equity": {"enabled": true, "net": 56200.5,
                             "available": {"cash": 60000, "collateral": 8000},
                             "utilised": {"debits": 12000, "m2m_unrealised": 0}},
                  "commodity": {"enabled": true, "net": 1500}
                }""");

        JSONObject m = DashBoardService.availableMargin(margins);

        assertMoney("56200.50", m.getBigDecimal("value"));
        assertMoney("12000.00", m.getBigDecimal("used"));
        assertMoney("60000.00", m.getBigDecimal("cash"));
        assertMoney("1500.00", m.getBigDecimal("commodity"));
    }

    @Test
    void disabledSegmentsGiveZeroAndNull() {
        JSONObject margins = new JSONObject("""
                {"equity": {"enabled": false, "net": 999}, "commodity": {"enabled": false, "net": 5}}""");

        JSONObject m = DashBoardService.availableMargin(margins);

        assertMoney("0.00", m.getBigDecimal("value"));
        assertTrue(m.isNull("commodity"));
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }
}
