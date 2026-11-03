package com.app.algodesk.service;

import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;

@Service
public class DashBoardService {

    private static final BigDecimal HUNDRED = BigDecimal.valueOf(100);

    private final PortfolioService portfolioService;
    private final UserProfileService userProfileService;

    public DashBoardService(PortfolioService portfolioService, UserProfileService userProfileService) {
        this.portfolioService = portfolioService;
        this.userProfileService = userProfileService;
    }

    public JSONObject getDetailedSummary() throws Exception {
        // Kite: holdings = [ {...}, ... ], positions = { "net": [...], "day": [...] },
        //       margins = { "equity": {...}, "commodity": {...} }
        JSONArray holdings = new JSONArray(portfolioService.getHoldings());
        JSONArray netPositions = new JSONObject(portfolioService.getPositions()).getJSONArray("net");
        // getMarginDetails returns the full {"status","data"} response, not just data
        JSONObject margins = new JSONObject(userProfileService.getMarginDetails()).getJSONObject("data");

        JSONObject kpis = new JSONObject()
                .put("todayPnl", todayPnl(holdings, netPositions))
                .put("portfolio", portfolio(holdings))
                .put("availableMargin", availableMargin(margins));

        return new JSONObject().put("kpis", kpis);
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
