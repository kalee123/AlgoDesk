package com.app.algodesk.service;

import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.authentication.RqInfo;
import com.app.algodesk.broker.kite.KiteApiClient;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.stream.Collectors;

// Market-wide numbers (not tied to the user's account), e.g. the index strip on the dashboard.
@Service
public class MarketService {

    // {exchange, tradingsymbol, short label}. Kite's quote key is "EXCHANGE:TRADINGSYMBOL".
    static final List<String[]> INDICES = List.of(
            new String[]{"NSE", "NIFTY 50", "NIFTY 50"},
            new String[]{"NSE", "NIFTY BANK", "BANK NIFTY"},
            new String[]{"BSE", "SENSEX", "SENSEX"}
    );

    private final KiteApiClient kiteApiClient;
    private final RqInfo rqInfo;

    public MarketService(KiteApiClient kiteApiClient, RqInfo rqInfo) {
        this.kiteApiClient = kiteApiClient;
        this.rqInfo = rqInfo;
    }

    // Returns [{ exchange, symbol, label, ltp, previousClose, change, changePercent }]
    public String getIndices() throws Exception {
        String query = INDICES.stream()
                .map(i -> "i=" + URLEncoder.encode(i[0] + ":" + i[1], StandardCharsets.UTF_8))
                .collect(Collectors.joining("&"));
        // data = { "NSE:NIFTY 50": { "last_price": ..., "ohlc": { "open", "high", "low", "close" } }, ... }
        JSONObject quotes = new JSONObject(kiteApiClient.getData(UrlConstants.KITE_QUOTE_OHLC_PATH + "?" + query,
                rqInfo.getAccessToken()));
        return toIndices(quotes).toString();
    }

    // ohlc.close in a quote is the PREVIOUS day's close, so change = last_price - ohlc.close
    static JSONArray toIndices(JSONObject quotes) {
        JSONArray result = new JSONArray();
        for (String[] index : INDICES) {
            JSONObject quote = quotes.optJSONObject(index[0] + ":" + index[1]);
            if (quote == null) {
                continue;   // Kite leaves out instruments it couldn't quote
            }
            BigDecimal ltp = quote.optBigDecimal("last_price", BigDecimal.ZERO);
            BigDecimal previousClose = quote.optJSONObject("ohlc", new JSONObject()).optBigDecimal("close", BigDecimal.ZERO);
            BigDecimal change = ltp.subtract(previousClose);
            Object changePercent = previousClose.signum() == 0
                    ? JSONObject.NULL
                    : change.multiply(BigDecimal.valueOf(100)).divide(previousClose, 2, RoundingMode.HALF_UP);

            result.put(new JSONObject()
                    .put("exchange", index[0])
                    .put("symbol", index[1])
                    .put("label", index[2])
                    .put("ltp", ltp)
                    .put("previousClose", previousClose)
                    .put("change", change.setScale(2, RoundingMode.HALF_UP))
                    .put("changePercent", changePercent));
        }
        return result;
    }
}
