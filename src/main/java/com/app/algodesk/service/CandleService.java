package com.app.algodesk.service;

import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.authentication.RqInfo;
import com.app.algodesk.broker.kite.KiteApiClient;
import com.app.algodesk.repository.InstrumentRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

// Historical candles for the chart, fetched from Kite on demand (not stored).
@Service
public class CandleService {

    private static final ZoneId IST = ZoneId.of("Asia/Kolkata");
    private static final int TARGET_CANDLES = 500;
    private static final int MARKET_MINUTES_PER_DAY = 375;   // NSE 09:15 - 15:30

    // Kite interval -> {minutes per candle, max days Kite allows in one request}
    private static final Map<String, int[]> INTERVALS = Map.of(
            "minute", new int[]{1, 60},
            "3minute", new int[]{3, 100},
            "5minute", new int[]{5, 100},
            "10minute", new int[]{10, 100},
            "15minute", new int[]{15, 200},
            "30minute", new int[]{30, 200},
            "60minute", new int[]{60, 400},
            "day", new int[]{MARKET_MINUTES_PER_DAY, 2000}
    );

    private static final DateTimeFormatter KITE_PARAM_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    // Kite candle time looks like 2026-11-02T09:15:00+0530 (offset without a colon)
    private static final DateTimeFormatter KITE_CANDLE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

    private final KiteApiClient kiteApiClient;
    private final InstrumentRepository instrumentRepository;
    private final RqInfo rqInfo;

    public CandleService(KiteApiClient kiteApiClient, InstrumentRepository instrumentRepository, RqInfo rqInfo) {
        this.kiteApiClient = kiteApiClient;
        this.instrumentRepository = instrumentRepository;
        this.rqInfo = rqInfo;
    }

    // Returns [{time, open, high, low, close, volume}, ...] with time in epoch seconds (what chart libraries expect).
    // from/to are optional: when missing, the range is chosen to give about 500 candles for the interval.
    public String getHistoricCandles(String exchange, String symbol, String interval,
                                     LocalDate from, LocalDate to) throws Exception {
        int[] intervalInfo = INTERVALS.get(interval);
        if (intervalInfo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown interval '" + interval + "'. Use one of " + INTERVALS.keySet());
        }
        int minutesPerCandle = intervalInfo[0];
        int maxDays = intervalInfo[1];

        LocalDate endDate = to != null ? to : LocalDate.now(IST);
        LocalDate startDate = from != null ? from : endDate.minusDays(daysFor500Candles(minutesPerCandle, maxDays));
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
        if (startDate.plusDays(maxDays).isBefore(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Range too long for " + interval + ": Kite allows at most " + maxDays + " days per request");
        }

        long token = getSymbolVsExchangeId(exchange, symbol);

        String path = UrlConstants.KITE_INSTRUMENTS_HISTORICAL_PATH + "/" + token + "/" + interval
                + "?from=" + encode(startDate.atStartOfDay().format(KITE_PARAM_FORMAT))
                + "&to=" + encode(endDate.atTime(23, 59, 59).format(KITE_PARAM_FORMAT));

        // data = {"candles": [["2026-11-02T09:15:00+0530", open, high, low, close, volume], ...]}
        JSONArray kiteCandles = new JSONObject(kiteApiClient.getData(path, rqInfo.getAccessToken()))
                .getJSONArray("candles");

        // The day-count estimate is rough (holidays, MCX's longer hours), so only keep the latest 500
        // when the range was chosen by us; an explicit from/to gets everything in it.
        int first = (from == null) ? Math.max(0, kiteCandles.length() - TARGET_CANDLES) : 0;

        JSONArray candles = new JSONArray();
        for (int i = first; i < kiteCandles.length(); i++) {
            JSONArray c = kiteCandles.getJSONArray(i);
            candles.put(new JSONObject()
                    .put("time", OffsetDateTime.parse(c.getString(0), KITE_CANDLE_FORMAT).toEpochSecond())
                    .put("open", c.getBigDecimal(1))
                    .put("high", c.getBigDecimal(2))
                    .put("low", c.getBigDecimal(3))
                    .put("close", c.getBigDecimal(4))
                    .put("volume", c.getLong(5)));
        }
        return candles.toString();
    }

    // e.g. 5minute: 375 / 5 = 75 candles a day -> 500 / 75 = 7 trading days -> ~10 calendar days (+ weekends)
    private static long daysFor500Candles(int minutesPerCandle, int maxDays) {
        double candlesPerDay = (double) MARKET_MINUTES_PER_DAY / minutesPerCandle;
        long tradingDays = (long) Math.ceil(TARGET_CANDLES / candlesPerDay);
        long calendarDays = tradingDays * 7 / 5 + 3;   // weekends, plus a little slack for holidays
        return Math.min(calendarDays, maxDays);
    }

    // The browser only knows exchange + symbol; the Kite token stays on the server
    private long getSymbolVsExchangeId(String exchange, String symbol) {
        return instrumentRepository.findByExchangeAndTradingsymbol(exchange.toUpperCase(), symbol.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown instrument " + exchange + ":" + symbol))
                .getInstrumentToken();
    }

    private static String encode(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }
}
