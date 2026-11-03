package com.app.algodesk.service;

import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.authentication.RqInfo;
import com.app.algodesk.broker.kite.KiteApiClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

// Calculation tests use hand-made Kite-shaped JSON; summary tests fake KiteApiClient with Mockito.
// No Spring, no network.
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

    // ---------- Orders ----------

    private static final JSONArray ORDERS = new JSONArray("""
            [
              {"order_timestamp":"2026-11-04 09:21:05","transaction_type":"BUY","exchange":"NSE","tradingsymbol":"INFY",
               "quantity":10,"filled_quantity":10,"average_price":1140.5,"price":0,"status":"COMPLETE"},
              {"order_timestamp":"2026-11-04 11:40:00","transaction_type":"BUY","exchange":"NSE","tradingsymbol":"SBIN",
               "quantity":20,"filled_quantity":0,"average_price":0,"price":810,"status":"OPEN"},
              {"order_timestamp":"2026-11-04 10:05:30","transaction_type":"SELL","exchange":"NSE","tradingsymbol":"TCS",
               "quantity":5,"filled_quantity":0,"average_price":0,"price":4000,"status":"REJECTED"},
              {"order_timestamp":"2026-11-04 10:30:00","transaction_type":"BUY","exchange":"NFO","tradingsymbol":"NIFTY26NOVFUT",
               "quantity":75,"filled_quantity":0,"average_price":0,"price":23500,"status":"TRIGGER PENDING"},
              {"order_timestamp":"2026-11-04 09:50:00","transaction_type":"SELL","exchange":"NSE","tradingsymbol":"ITC",
               "quantity":1,"filled_quantity":0,"average_price":0,"price":400,"status":"CANCELLED"}
            ]""");

    @Test
    void orderCountsTreatUnfinishedStatusesAsOpen() {
        JSONObject c = DashBoardService.orderCounts(ORDERS);

        assertEquals(2, c.getInt("open"));        // OPEN + TRIGGER PENDING
        assertEquals(1, c.getInt("executed"));
        assertEquals(1, c.getInt("rejected"));
        assertEquals(1, c.getInt("cancelled"));
        assertEquals(5, c.getInt("total"));
    }

    @Test
    void recentOrdersNewestFirstAndLimited() {
        JSONArray recent = DashBoardService.recentOrders(ORDERS, 3);

        assertEquals(3, recent.length());
        assertEquals("SBIN", recent.getJSONObject(0).getString("symbol"));          // 11:40
        assertEquals("11:40", recent.getJSONObject(0).getString("time"));
        assertEquals("NIFTY26NOVFUT", recent.getJSONObject(1).getString("symbol")); // 10:30
        assertEquals("TCS", recent.getJSONObject(2).getString("symbol"));           // 10:05
    }

    @Test
    void recentOrderPriceUsesFillPriceElseOrderPrice() {
        JSONArray recent = DashBoardService.recentOrders(ORDERS, 5);

        JSONObject infy = recent.getJSONObject(4);   // oldest, filled
        assertMoney("1140.50", infy.getBigDecimal("price"));
        JSONObject sbin = recent.getJSONObject(0);   // open, not filled
        assertMoney("810.00", sbin.getBigDecimal("price"));
        assertTrue(sbin.getBoolean("open"));
    }

    // ---------- Movers ----------

    @Test
    void moversSortedByBiggestMoveEitherDirection() {
        JSONArray holdings = new JSONArray("""
                [
                  {"exchange":"NSE","tradingsymbol":"SMALL","quantity":1,"t1_quantity":0,"last_price":101,"close_price":100},
                  {"exchange":"NSE","tradingsymbol":"DOWN","quantity":2,"t1_quantity":0,"last_price":90,"close_price":100},
                  {"exchange":"NSE","tradingsymbol":"UP","quantity":3,"t1_quantity":0,"last_price":105,"close_price":100},
                  {"exchange":"NSE","tradingsymbol":"NEW","quantity":1,"t1_quantity":0,"last_price":50,"close_price":0}
                ]""");

        JSONArray movers = DashBoardService.topMovers(holdings, 2);

        assertEquals(2, movers.length());
        assertEquals("DOWN", movers.getJSONObject(0).getString("symbol"));   // -10%
        assertMoney("-10.00", movers.getJSONObject(0).getBigDecimal("dayChangePercent"));
        assertMoney("-20.00", movers.getJSONObject(0).getBigDecimal("dayPnl"));
        assertEquals("UP", movers.getJSONObject(1).getString("symbol"));     // +5%
    }

    // ---------- Positions ----------

    @Test
    void openPositionsSkipClosedOnes() {
        JSONArray positions = new JSONArray("""
                [
                  {"exchange":"NFO","tradingsymbol":"NIFTY26NOVFUT","product":"NRML","quantity":75,
                   "last_price":23600,"pnl":5000,"m2m":1200},
                  {"exchange":"NSE","tradingsymbol":"SBIN","product":"MIS","quantity":0,"last_price":810,"pnl":-300,"m2m":-300},
                  {"exchange":"NSE","tradingsymbol":"ITC","product":"MIS","quantity":-10,"last_price":400,"pnl":50,"m2m":50}
                ]""");

        JSONArray open = DashBoardService.openPositions(positions);

        assertEquals(2, open.length());
        assertEquals("NIFTY26NOVFUT", open.getJSONObject(0).getString("symbol"));
        assertEquals(-10, open.getJSONObject(1).getInt("quantity"));   // short position
    }

    // ---------- Whole summary: parallel calls and partial failure ----------

    private static final String TOKEN = "test-token";

    private KiteApiClient kiteReturningEverything() throws Exception {
        KiteApiClient kite = mock(KiteApiClient.class);
        when(kite.getData(eq(UrlConstants.KITE_HOLDINGS_PATH), eq(TOKEN))).thenReturn("""
                [{"exchange":"NSE","tradingsymbol":"RELIANCE","quantity":10,"t1_quantity":0,
                  "last_price":1300,"close_price":1280,"average_price":1200}]""");
        when(kite.getData(eq(UrlConstants.KITE_POSITIONS_PATH), eq(TOKEN))).thenReturn("""
                {"net":[{"tradingsymbol":"SBIN","quantity":0,"m2m":-50}],"day":[]}""");
        when(kite.getData(eq(UrlConstants.KITE_ORDERS_PATH), eq(TOKEN))).thenReturn(ORDERS.toString());
        when(kite.getData(eq(UrlConstants.KITE_USER_MARGINS_PATH), eq(TOKEN))).thenReturn("""
                {"equity":{"enabled":true,"net":5000,"available":{"cash":6000},"utilised":{"debits":1000}}}""");
        return kite;
    }

    private static RqInfo rqInfoWithToken() {
        RqInfo rqInfo = mock(RqInfo.class);
        when(rqInfo.getAccessToken()).thenReturn(TOKEN);
        return rqInfo;
    }

    @Test
    void summaryCombinesAllSections() throws Exception {
        DashBoardService service = new DashBoardService(kiteReturningEverything(), rqInfoWithToken());

        JSONObject summary = service.getDetailedSummary();

        JSONObject kpis = summary.getJSONObject("kpis");
        assertMoney("150.00", kpis.getJSONObject("todayPnl").getBigDecimal("value"));   // +200 holdings, -50 positions
        assertMoney("13000.00", kpis.getJSONObject("portfolio").getBigDecimal("value"));
        assertMoney("5000.00", kpis.getJSONObject("availableMargin").getBigDecimal("value"));
        assertEquals(2, kpis.getJSONObject("orders").getInt("open"));
        assertEquals(1, summary.getJSONArray("movers").length());
        assertEquals(0, summary.getJSONArray("positions").length());   // the only position is closed
        assertTrue(summary.getJSONObject("errors").isEmpty());
        assertTrue(summary.has("asOf"));
    }

    @Test
    void oneFailedCallOnlyBlanksItsOwnSections() throws Exception {
        KiteApiClient kite = kiteReturningEverything();
        when(kite.getData(eq(UrlConstants.KITE_ORDERS_PATH), eq(TOKEN))).thenThrow(new RuntimeException("Kite 500"));
        DashBoardService service = new DashBoardService(kite, rqInfoWithToken());

        JSONObject summary = service.getDetailedSummary();

        assertTrue(summary.getJSONObject("kpis").isNull("orders"));
        assertTrue(summary.isNull("recentOrders"));
        assertEquals("Could not load orders", summary.getJSONObject("errors").getString("orders"));
        // everything else still there
        assertMoney("150.00", summary.getJSONObject("kpis").getJSONObject("todayPnl").getBigDecimal("value"));
        assertEquals(1, summary.getJSONArray("movers").length());
    }

    @Test
    void failedPositionsBlankTodayPnlButNotPortfolio() throws Exception {
        KiteApiClient kite = kiteReturningEverything();
        when(kite.getData(eq(UrlConstants.KITE_POSITIONS_PATH), eq(TOKEN))).thenThrow(new RuntimeException("boom"));
        DashBoardService service = new DashBoardService(kite, rqInfoWithToken());

        JSONObject summary = service.getDetailedSummary();

        assertTrue(summary.getJSONObject("kpis").isNull("todayPnl"));   // needs holdings AND positions
        assertMoney("13000.00", summary.getJSONObject("kpis").getJSONObject("portfolio").getBigDecimal("value"));
    }

    @Test
    void everythingFailingIsAnError() throws Exception {
        KiteApiClient kite = mock(KiteApiClient.class);
        when(kite.getData(anyString(), anyString())).thenThrow(new RuntimeException("Kite down"));
        DashBoardService service = new DashBoardService(kite, rqInfoWithToken());

        assertThrows(IllegalStateException.class, service::getDetailedSummary);
    }

    private static void assertMoney(String expected, BigDecimal actual) {
        assertEquals(0, new BigDecimal(expected).compareTo(actual), "expected " + expected + " but was " + actual);
    }
}
