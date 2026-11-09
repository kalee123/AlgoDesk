package com.app.algodesk.service;

import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.authentication.RqInfo;
import com.app.algodesk.broker.kite.KiteApiClient;
import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.HistoricalDataRequest;
import com.app.algodesk.repository.InstrumentRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
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

    // Candles oldest first, time in epoch seconds (what chart libraries expect); oi is set only when request.isOi().
    // from/to are optional: when missing, the range is chosen to give about 500 candles for the interval.
    public List<CandleInfo> getHistoricCandles(HistoricalDataRequest request) throws Exception {
        String interval = request.getInterval();
        int[] intervalInfo = INTERVALS.get(interval);
        if (intervalInfo == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Unknown interval '" + interval + "'. Use one of " + INTERVALS.keySet());
        }
        int minutesPerCandle = intervalInfo[0];
        int maxDays = intervalInfo[1];

        // Whole days: 'to' runs to the end of its day, 'from' starts at midnight
        LocalDate endDate = request.getTo() != null ? request.getTo() : LocalDate.now(IST);
        LocalDate startDate = request.getFrom() != null ? request.getFrom()
                : endDate.minusDays(daysFor500Candles(minutesPerCandle, maxDays));
        LocalDateTime endTime = endDate.atTime(23, 59, 59);
        LocalDateTime startTime = startDate.atStartOfDay();
        if (startTime.isAfter(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
        if (startTime.plusDays(maxDays).isBefore(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Range too long for " + interval + ": Kite allows at most " + maxDays + " days per request");
        }

        long token = getSymbolVsExchangeId(request.getExchange(), request.getSymbol());

        String path = UrlConstants.KITE_INSTRUMENTS_HISTORICAL_PATH + "/" + token + "/" + interval
                + "?from=" + encode(startTime.format(KITE_PARAM_FORMAT))
                + "&to=" + encode(endTime.format(KITE_PARAM_FORMAT))
                + "&continuous=" + (request.isContinuous() ? 1 : 0)
                + "&oi=" + (request.isOi() ? 1 : 0);

        // data = {"candles": [["2026-11-02T09:15:00+0530", open, high, low, close, volume(, oi)], ...]}
        JSONArray kiteCandles = new JSONObject(kiteApiClient.getData(path, rqInfo.getAccessToken()))
                .getJSONArray("candles");

        // The day-count estimate is rough (holidays, MCX's longer hours), so only keep the latest 500
        // when the range was chosen by us; an explicit from/to gets everything in it.
        int first = (request.getFrom() == null) ? Math.max(0, kiteCandles.length() - TARGET_CANDLES) : 0;

        List<CandleInfo> candles = new ArrayList<>();
        for (int i = first; i < kiteCandles.length(); i++) {
            JSONArray c = kiteCandles.getJSONArray(i);
            CandleInfo candle = new CandleInfo(
                    OffsetDateTime.parse(c.getString(0), KITE_CANDLE_FORMAT).toEpochSecond(),
                    c.getDouble(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getLong(5));
            if (request.isOi()) {
                candle.setOi(c.getLong(6));
            }
            candles.add(candle);
        }
        return candles;
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

//    public LinkedHashMap<String, List<CandleInfo>> getCandlesByDate(List<CandleInfo> candles) {
//        LinkedHashMap<String, List<CandleInfo>> candlesByDate = new LinkedHashMap<>();
//        for (CandleInfo candle : candles) {
//            String date = Instant.ofEpochSecond(candle.getTime()).atZone(IST).toLocalDate().toString();
//            candlesByDate.computeIfAbsent(date, d -> new ArrayList<>()).add(candle);
//        }
//        return candlesByDate;
//    }
}
