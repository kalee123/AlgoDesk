package com.app.algodesk.service;

import com.app.algodesk.appsetup.Intervals;
import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.authentication.RqInfo;
import com.app.algodesk.authentication.UserAuthService;
import com.app.algodesk.broker.kite.KiteApiClient;
import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.CandleRange;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.app.algodesk.appsetup.FieldConstants.KITE_CANDLE_FORMAT;
import static com.app.algodesk.appsetup.FieldConstants.KITE_PARAM_FORMAT;

// Everything Kite-specific about historical candles: per-call day limit (chunks), pacing, URL, token, JSON.
// Package-private: only classes in this package (CandleService) can use it.
@Component
class KiteCandleHandler {

    private static final Logger log = LoggerFactory.getLogger(KiteCandleHandler.class);
    private static final long KITE_CALL_GAP_MS = 350; // Kite allows ~3 historical calls a second

    private final KiteApiClient kiteApiClient;
    private final RqInfo rqInfo;
    private final UserAuthService userAuthService;

    KiteCandleHandler(KiteApiClient kiteApiClient, RqInfo rqInfo, UserAuthService userAuthService) {
        this.kiteApiClient = kiteApiClient;
        this.rqInfo = rqInfo;
        this.userAuthService = userAuthService;
    }

    // Candles for any range: split into Kite-sized chunks, fetch each (paced), join them oldest first
    List<CandleInfo> fetchCandles(long instrumentToken, Intervals timeFrame, LocalDate startDate, LocalDate endDate,
                                  boolean isContinuous, boolean isOi) throws Exception {
        List<CandleRange> chunks = rangeChunksBuilder(timeFrame, startDate, endDate, isContinuous, isOi);
        String accessToken = getAccessToken();
        List<CandleInfo> allCandles = new ArrayList<>();
        for (int i = 0; i < chunks.size(); i++) {
            if (i > 0) {
                Thread.sleep(KITE_CALL_GAP_MS);
            }
            long startMs = System.currentTimeMillis();
            String candleData = getCandleData(kiteCandleUrlBuilder(instrumentToken, chunks.get(i)), accessToken);
            List<CandleInfo> chunkCandles = getParsedCandle(candleData, isOi);
            allCandles.addAll(chunkCandles);
            // Spring's log prefix shows the thread name, e.g. which strategy thread made this call
            log.info("token {} chunk {}/{}: {} candles in {} ms", instrumentToken, i + 1, chunks.size(),
                    chunkCandles.size(), System.currentTimeMillis() - startMs);
        }
        return allCandles;
    }

    // Kite allows at most interval.maxDays per call; each chunk covers whole days, no gaps or overlaps
    private List<CandleRange> rangeChunksBuilder(Intervals timeFrame, LocalDate startDate, LocalDate endDate,
                                                 boolean isContinuous, boolean isOi) {
        List<CandleRange> candleRange = new ArrayList<>();
        int maxDays = timeFrame.getMaxDays();
        LocalDate chunkStart = startDate;

        while (!chunkStart.isAfter(endDate)) {
            LocalDate chunkEnd = chunkStart.plusDays(maxDays - 1);
            if (chunkEnd.isAfter(endDate)) {
                chunkEnd = endDate;
            }
            candleRange.add(new CandleRange(timeFrame, chunkStart.atStartOfDay(), chunkEnd.atTime(23, 59, 59),
                    isContinuous, isOi));
            chunkStart = chunkEnd.plusDays(1);
        }
        return candleRange;
    }

    // RqInfo throws (not null) outside an HTTP request, so check for a request first; else use the DB token
    private String getAccessToken() {
        return RequestContextHolder.getRequestAttributes() != null
                ? rqInfo.getAccessToken()
                : userAuthService.getAnyActiveAccessToken()
                        .orElseThrow(() -> new IllegalStateException("No valid broker token, login again"));
    }

    private String getCandleData(String kiteCandleUrl, String accessToken) throws Exception {
        return kiteApiClient.getData(kiteCandleUrl, accessToken);
    }

    // Kite historical path, e.g. /instruments/historical/738561/5minute?from=...&to=...&continuous=0&oi=0
    private static String kiteCandleUrlBuilder(long instrumentToken, CandleRange range) {
        // The builder adds ? & / and encodes the values (e.g. the space in "2025-01-01 00:00:00")
        return UriComponentsBuilder.fromPath(UrlConstants.KITE_INSTRUMENTS_HISTORICAL_PATH)
                .pathSegment(String.valueOf(instrumentToken), range.getInterval().getCode())
                .queryParam("from", range.getStartTime().format(KITE_PARAM_FORMAT))
                .queryParam("to", range.getEndTime().format(KITE_PARAM_FORMAT))
                .queryParam("continuous", range.isContinuous() ? 1 : 0)
                .queryParam("oi", range.isOi() ? 1 : 0)
                .encode()
                .toUriString();
    }

    // Kite candle: [time, open, high, low, close, volume, (oi)]
    private static List<CandleInfo> getParsedCandle(String data, boolean withOi) {
        JSONArray kiteCandles = new JSONObject(data).getJSONArray("candles");
        List<CandleInfo> candles = new ArrayList<>(kiteCandles.length());
        for (int i = 0; i < kiteCandles.length(); i++) {
            JSONArray c = kiteCandles.getJSONArray(i);
            CandleInfo candle = new CandleInfo(
                    OffsetDateTime.parse(c.getString(0), KITE_CANDLE_FORMAT).toEpochSecond(),
                    c.getDouble(1), c.getDouble(2), c.getDouble(3), c.getDouble(4), c.getLong(5));
            if (withOi) {
                candle.setOi(c.getLong(6));
            }
            candles.add(candle);
        }
        return candles;
    }
}
