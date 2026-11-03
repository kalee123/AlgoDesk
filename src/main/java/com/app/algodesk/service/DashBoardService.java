package com.app.algodesk.service;

import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.authentication.RqInfo;
import com.app.algodesk.broker.kite.KiteApiClient;
import jakarta.annotation.PreDestroy;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Function;

// Builds everything the dashboard shows in one response (Backend-for-Frontend):
// fetches from Kite in parallel, then runs pure calculation methods on the results.
@Service
public class DashBoardService {

    private static final Logger log = LoggerFactory.getLogger(DashBoardService.class);

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);
    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final long KITE_TIMEOUT_SECONDS = 5;
    private static final int TOP_MOVERS = 5;
    private static final int RECENT_ORDERS = 5;
    private static final Set<String> FINISHED_ORDER_STATUSES = Set.of("COMPLETE", "REJECTED", "CANCELLED");

    private final KiteApiClient kiteApiClient;
    private final RqInfo rqInfo;

    // Own small pool for blocking HTTP calls; the shared ForkJoinPool is meant for CPU work
    private final ExecutorService kiteCalls = Executors.newFixedThreadPool(8);

    public DashBoardService(KiteApiClient kiteApiClient, RqInfo rqInfo) {
        this.kiteApiClient = kiteApiClient;
        this.rqInfo = rqInfo;
    }

    @PreDestroy
    void shutdown() {
        kiteCalls.shutdown();
    }

    public JSONObject getDetailedSummary() throws Exception {
        // RqInfo is request-scoped: it only works on this (the request's) thread.
        // Read the token here, then hand it to the background calls.
        String token = rqInfo.getAccessToken();

        // 1. Start all four Kite calls at once. Total wait = the slowest call, not the sum.
        CompletableFuture<JSONArray> holdingsCall = fetch(UrlConstants.KITE_HOLDINGS_PATH, token, JSONArray::new);
        CompletableFuture<JSONObject> positionsCall = fetch(UrlConstants.KITE_POSITIONS_PATH, token, JSONObject::new);
        CompletableFuture<JSONArray> ordersCall = fetch(UrlConstants.KITE_ORDERS_PATH, token, JSONArray::new);
        CompletableFuture<JSONObject> marginsCall = fetch(UrlConstants.KITE_USER_MARGINS_PATH, token, JSONObject::new);

        // 2. Wait for each. A failed call becomes null + an entry in errors, so the rest still shows.
        JSONObject errors = new JSONObject();
        JSONArray holdings = await("holdings", holdingsCall, errors);
        JSONObject positions = await("positions", positionsCall, errors);
        JSONArray orders = await("orders", ordersCall, errors);
        JSONObject margins = await("margins", marginsCall, errors);

        if (holdings == null && positions == null && orders == null && margins == null) {
            // Nothing to show at all: that's a failed request, not a partial one (-> 502 via ApiExceptionHandler)
            throw new IllegalStateException("All dashboard calls to Kite failed: " + errors);
        }
        JSONArray netPositions = positions == null ? null : positions.getJSONArray("net");

        // 3. Pure calculations. Each section is null when the data it needs is missing.
        JSONObject kpis = new JSONObject()
                .put("todayPnl", holdings != null && netPositions != null ? todayPnl(holdings, netPositions) : JSONObject.NULL)
                .put("portfolio", holdings != null ? portfolio(holdings) : JSONObject.NULL)
                .put("availableMargin", margins != null ? availableMargin(margins) : JSONObject.NULL)
                .put("orders", orders != null ? orderCounts(orders) : JSONObject.NULL);

        return new JSONObject()
                .put("asOf", OffsetDateTime.now(IST).withNano(0).toString())
                .put("kpis", kpis)
                .put("movers", holdings != null ? topMovers(holdings, TOP_MOVERS) : JSONObject.NULL)
                .put("recentOrders", orders != null ? recentOrders(orders, RECENT_ORDERS) : JSONObject.NULL)
                .put("positions", netPositions != null ? openPositions(netPositions) : JSONObject.NULL)
                .put("errors", errors);
    }

    // Runs one Kite GET on the pool and parses its "data" part; gives up after KITE_TIMEOUT_SECONDS
    private <T> CompletableFuture<T> fetch(String path, String token, Function<String, T> parse) {
        return CompletableFuture
                .supplyAsync(() -> {
                    try {
                        return parse.apply(kiteApiClient.getData(path, token));
                    } catch (Exception e) {
                        throw new CompletionException(e);   // lambdas can't throw checked exceptions
                    }
                }, kiteCalls)
                .orTimeout(KITE_TIMEOUT_SECONDS, TimeUnit.SECONDS);
    }

    private static <T> T await(String name, CompletableFuture<T> call, JSONObject errors) {
        try {
            return call.join();
        } catch (CompletionException e) {
            Throwable cause = e.getCause() != null ? e.getCause() : e;
            String reason = cause instanceof TimeoutException ? "Kite request timed out" : "Could not load " + name;
            log.warn("Dashboard: {} failed", name, cause);
            errors.put(name, reason);
            return null;
        }
    }

    // Long-term holdings at today's price vs what was paid for them.
    // Returns { value, invested, pnl, pnlPercent }.
    static JSONObject portfolio(JSONArray holdings) {
        BigDecimal value = BigDecimal.ZERO;
        BigDecimal invested = BigDecimal.ZERO;

        for (int i = 0; i < holdings.length(); i++) {
            JSONObject h = holdings.getJSONObject(i);
            BigDecimal qty = holdingQty(h);
            value = value.add(h.optBigDecimal("last_price", BigDecimal.ZERO).multiply(qty));
            invested = invested.add(h.optBigDecimal("average_price", BigDecimal.ZERO).multiply(qty));
        }

        BigDecimal pnl = value.subtract(invested);
        BigDecimal pnlPercent = invested.signum() == 0
                ? null
                : pnl.multiply(HUNDRED).divide(invested, 2, RoundingMode.HALF_UP);

        return new JSONObject()
                .put("value", money(value))
                .put("invested", money(invested))
                .put("pnl", money(pnl))
                .put("pnlPercent", pnlPercent == null ? JSONObject.NULL : pnlPercent);
    }

    // Equity segment is what stock and F&O trading uses. Kite's "net" is the margin you can still use;
    // "utilised.debits" is what open orders/positions are blocking.
    // Returns { value, used, cash, commodity }; commodity is null when that segment isn't enabled.
    static JSONObject availableMargin(JSONObject margins) {
        JSONObject equity = margins.optJSONObject("equity");
        JSONObject commodity = margins.optJSONObject("commodity");

        BigDecimal available = BigDecimal.ZERO;
        BigDecimal used = BigDecimal.ZERO;
        BigDecimal cash = BigDecimal.ZERO;
        if (isEnabled(equity)) {
            available = equity.optBigDecimal("net", BigDecimal.ZERO);
            used = equity.optJSONObject("utilised", new JSONObject()).optBigDecimal("debits", BigDecimal.ZERO);
            cash = equity.optJSONObject("available", new JSONObject()).optBigDecimal("cash", BigDecimal.ZERO);
        }

        return new JSONObject()
                .put("value", money(available))
                .put("used", money(used))
                .put("cash", money(cash))
                .put("commodity", isEnabled(commodity)
                        ? money(commodity.optBigDecimal("net", BigDecimal.ZERO))
                        : JSONObject.NULL);
    }

    // Kite order statuses: COMPLETE / REJECTED / CANCELLED are final; anything else
    // (OPEN, TRIGGER PENDING, AMO REQ RECEIVED, ...) is still waiting at the exchange.
    // Returns { open, executed, rejected, cancelled, total }.
    static JSONObject orderCounts(JSONArray orders) {
        int open = 0, executed = 0, rejected = 0, cancelled = 0;
        for (int i = 0; i < orders.length(); i++) {
            switch (orders.getJSONObject(i).optString("status")) {
                case "COMPLETE" -> executed++;
                case "REJECTED" -> rejected++;
                case "CANCELLED" -> cancelled++;
                default -> open++;
            }
        }
        return new JSONObject()
                .put("open", open)
                .put("executed", executed)
                .put("rejected", rejected)
                .put("cancelled", cancelled)
                .put("total", orders.length());
    }

    static boolean isOpenOrder(JSONObject order) {
        return !FINISHED_ORDER_STATUSES.contains(order.optString("status"));
    }

    // Newest first. Kite timestamps look like "2026-11-04 09:21:05", so string order = time order.
    // Returns [{ time, side, exchange, symbol, quantity, filled, price, status, open }].
    static JSONArray recentOrders(JSONArray orders, int limit) {
        List<JSONObject> list = new ArrayList<>();
        for (int i = 0; i < orders.length(); i++) {
            list.add(orders.getJSONObject(i));
        }
        list.sort(Comparator.comparing((JSONObject o) -> o.optString("order_timestamp")).reversed());

        JSONArray result = new JSONArray();
        for (JSONObject o : list.subList(0, Math.min(limit, list.size()))) {
            String timestamp = o.optString("order_timestamp");
            BigDecimal price = o.optBigDecimal("average_price", BigDecimal.ZERO);
            if (price.signum() == 0) {
                price = o.optBigDecimal("price", BigDecimal.ZERO);   // not filled yet: show the order price
            }
            result.put(new JSONObject()
                    .put("time", timestamp.length() >= 16 ? timestamp.substring(11, 16) : timestamp)
                    .put("side", o.optString("transaction_type"))
                    .put("exchange", o.optString("exchange"))
                    .put("symbol", o.optString("tradingsymbol"))
                    .put("quantity", o.optInt("quantity"))
                    .put("filled", o.optInt("filled_quantity"))
                    .put("price", money(price))
                    .put("status", o.optString("status"))
                    .put("open", isOpenOrder(o)));
        }
        return result;
    }

    // Holdings that moved the most today, up or down (by size of the % move).
    // Returns [{ exchange, symbol, ltp, dayChangePercent, dayPnl }].
    static JSONArray topMovers(JSONArray holdings, int limit) {
        List<JSONObject> movers = new ArrayList<>();
        for (int i = 0; i < holdings.length(); i++) {
            JSONObject h = holdings.getJSONObject(i);
            BigDecimal lastPrice = h.optBigDecimal("last_price", BigDecimal.ZERO);
            BigDecimal closePrice = h.optBigDecimal("close_price", BigDecimal.ZERO);
            if (closePrice.signum() == 0) {
                continue;
            }
            BigDecimal changePercent = lastPrice.subtract(closePrice).multiply(HUNDRED)
                    .divide(closePrice, 2, RoundingMode.HALF_UP);
            movers.add(new JSONObject()
                    .put("exchange", h.optString("exchange"))
                    .put("symbol", h.optString("tradingsymbol"))
                    .put("ltp", lastPrice)
                    .put("dayChangePercent", changePercent)
                    .put("dayPnl", money(lastPrice.subtract(closePrice).multiply(holdingQty(h)))));
        }
        movers.sort(Comparator.comparing((JSONObject m) -> m.getBigDecimal("dayChangePercent").abs()).reversed());
        return new JSONArray(movers.subList(0, Math.min(limit, movers.size())));
    }

    // Positions still open (quantity != 0). Closed ones are already counted in today's P&L.
    // Returns [{ exchange, symbol, product, quantity, ltp, pnl, m2m }].
    static JSONArray openPositions(JSONArray netPositions) {
        JSONArray result = new JSONArray();
        for (int i = 0; i < netPositions.length(); i++) {
            JSONObject p = netPositions.getJSONObject(i);
            if (p.optInt("quantity") == 0) {
                continue;
            }
            result.put(new JSONObject()
                    .put("exchange", p.optString("exchange"))
                    .put("symbol", p.optString("tradingsymbol"))
                    .put("product", p.optString("product"))
                    .put("quantity", p.optInt("quantity"))
                    .put("ltp", p.optBigDecimal("last_price", BigDecimal.ZERO))
                    .put("pnl", money(p.optBigDecimal("pnl", BigDecimal.ZERO)))
                    .put("m2m", money(p.optBigDecimal("m2m", BigDecimal.ZERO))));
        }
        return result;
    }

    private static boolean isEnabled(JSONObject segment) {
        return segment != null && segment.optBoolean("enabled", true);
    }

    // T+1 shares (bought yesterday, not settled yet) are reported separately but are still owned
    private static BigDecimal holdingQty(JSONObject h) {
        return h.optBigDecimal("quantity", BigDecimal.ZERO).add(h.optBigDecimal("t1_quantity", BigDecimal.ZERO));
    }

    // Today's P&L = movement of long-term holdings since yesterday's close
    //             + mark-to-market of today's positions (intraday, F&O, and shares bought today).
    // Returns { value, percent, holdings, positions }; percent is relative to yesterday's holdings value.
    static JSONObject todayPnl(JSONArray holdings, JSONArray netPositions) {
        BigDecimal holdingsPnl = BigDecimal.ZERO;
        BigDecimal previousValue = BigDecimal.ZERO;

        for (int i = 0; i < holdings.length(); i++) {
            JSONObject h = holdings.getJSONObject(i);
            BigDecimal qty = holdingQty(h);
            BigDecimal lastPrice = h.optBigDecimal("last_price", BigDecimal.ZERO);
            BigDecimal closePrice = h.optBigDecimal("close_price", BigDecimal.ZERO);
            if (closePrice.signum() == 0) {
                continue;   // no previous close (e.g. newly listed): no "day change" to speak of
            }

            holdingsPnl = holdingsPnl.add(lastPrice.subtract(closePrice).multiply(qty));
            previousValue = previousValue.add(closePrice.multiply(qty));
        }

        // m2m, not pnl: for a position carried from yesterday, pnl is measured from the buy price,
        // while m2m is measured from yesterday's close, i.e. only today's part
        BigDecimal positionsPnl = BigDecimal.ZERO;
        for (int i = 0; i < netPositions.length(); i++) {
            positionsPnl = positionsPnl.add(netPositions.getJSONObject(i).optBigDecimal("m2m", BigDecimal.ZERO));
        }

        BigDecimal total = holdingsPnl.add(positionsPnl);
        BigDecimal percent = previousValue.signum() == 0
                ? null   // nothing held overnight: a percentage would be meaningless
                : total.multiply(HUNDRED).divide(previousValue, 2, RoundingMode.HALF_UP);

        return new JSONObject()
                .put("value", money(total))
                .put("percent", percent == null ? JSONObject.NULL : percent)
                .put("holdings", money(holdingsPnl))
                .put("positions", money(positionsPnl));
    }

    private static BigDecimal money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP);
    }
}
