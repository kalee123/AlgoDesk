package com.app.algodesk.service;

import com.app.algodesk.appsetup.Intervals;
import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.authentication.RqInfo;
import com.app.algodesk.authentication.UserAuthService;
import com.app.algodesk.broker.kite.KiteApiClient;
import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.CandleRange;
import com.app.algodesk.models.HistoricalDataRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import static com.app.algodesk.appsetup.FieldConstants.IST;
import static com.app.algodesk.appsetup.FieldConstants.KITE_CANDLE_FORMAT;
import static com.app.algodesk.appsetup.FieldConstants.KITE_PARAM_FORMAT;
import static com.app.algodesk.appsetup.FieldConstants.TOTAL_MARKET_MINUTES_PER_DAY;

@Service
public class CandleService {

    private static final Logger log = LoggerFactory.getLogger(CandleService.class);
    private static final int TARGET_CANDLES = 500;   // default range when no 'from' is given

    private final KiteApiClient kiteApiClient;
    private final InstrumentService instrumentService;
    private final RqInfo rqInfo;
    private final UserAuthService userAuthService;

    public CandleService(KiteApiClient kiteApiClient, InstrumentService instrumentService, RqInfo rqInfo,
                         UserAuthService userAuthService) {
        this.kiteApiClient = kiteApiClient;
        this.instrumentService = instrumentService;
        this.rqInfo = rqInfo;
        this.userAuthService = userAuthService;
    }

    public List<CandleInfo> getHistoricCandles(HistoricalDataRequest request) throws Exception {

        long instrumentToken = instrumentService.getInstrumentToken(request.getExchange(), request.getSymbol());
        List<String>  kiteCandleUrlLists = getCandleUrls(request);
        List<CandleInfo> allCandles = new ArrayList<>();
        for (String url : kiteCandleUrlLists) {
            String candleData = getCandleData(url);
            allCandles.addAll(getParsedCandle(candleData, request.isOi()));
        }
        return allCandles;
    }

    private List<String> getCandleUrls(HistoricalDataRequest request) throws Exception{
        long instrumentToken = instrumentService.getInstrumentToken(request.getExchange(), request.getSymbol());
        // The request carries the interval as text ("5minute"); turn it into the enum, 400 if unknown
        Intervals timeFrame = Intervals.getTimeframe(request.getInterval())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown interval '" + request.getInterval() + "'. Use one of " + Intervals.codes()));

        // from/to are optional: default 'to' = today, 'from' = about 500 candles back
        LocalDate endDate = request.getTo() != null ? request.getTo() : LocalDate.now(IST);
        LocalDate startDate = request.getFrom() != null ? request.getFrom()
                : endDate.minusDays(daysFor500Candles(timeFrame));
        if (startDate.isAfter(endDate)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }

        if (!checkValidRange(timeFrame, startDate, endDate)) {
            LocalDate trimmedStart = endDate.minusDays(timeFrame.getMaxTotalDays() - 1);
            log.warn("Range {} to {} too long for {}, trimmed to start at {}",
                    startDate, endDate, timeFrame.getCode(), trimmedStart);
            startDate = trimmedStart;
        }

        List<String> kiteUrls = new ArrayList<>();
        for (CandleRange range : getRangeChunks(timeFrame, startDate, endDate, request)) {
            kiteUrls.add(getCandleUrl(instrumentToken, range));
        }
        return kiteUrls;
    }

    private List<CandleRange> getRangeChunks(Intervals timeFrame,LocalDate startDate,LocalDate endDate,HistoricalDataRequest request){
        List<CandleRange> candleRange = new ArrayList<>();
        int maxDays = timeFrame.getMaxDays();
        LocalDate chunkStart = startDate;

        while (!chunkStart.isAfter(endDate)) {
            LocalDate chunkEnd = chunkStart.plusDays(maxDays - 1);
            if (chunkEnd.isAfter(endDate)) {
                chunkEnd = endDate;
            }
            candleRange.add(new CandleRange(timeFrame, chunkStart.atStartOfDay(), chunkEnd.atTime(23, 59, 59),request.isContinuous(),request.isOi()));
            chunkStart = chunkEnd.plusDays(1);
        }
        return candleRange;
    }

    // Each timeframe has its own total limit (Intervals.maxTotalDays), e.g. 1200 days of minute candles
    private static boolean checkValidRange(Intervals interval, LocalDate startDate, LocalDate endDate) {
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;   // both ends included
        if (totalDays <= interval.getMaxTotalDays()) {
            return true;
        }
        return false;
    }

    // e.g. 5minute: 375 / 5 = 75 candles a day -> 500 / 75 = 7 trading days -> ~10 calendar days (+ weekends)
    private static long daysFor500Candles(Intervals interval) {
        double candlesPerDay = (double) TOTAL_MARKET_MINUTES_PER_DAY / interval.getMinutesPerCandle();
        long tradingDays = (long) Math.ceil(TARGET_CANDLES / candlesPerDay);
        long calendarDays = tradingDays * 7 / 5 + 3;   // weekends, plus a little slack for holidays
        return Math.min(calendarDays, interval.getMaxDays());
    }

    // RqInfo throws (not null) outside an HTTP request, so check for a request first; else use the DB token
    private String getCandleData(String kiteCandleUrl) throws Exception {
        String accessToken = RequestContextHolder.getRequestAttributes() != null
                ? rqInfo.getAccessToken()
                : userAuthService.getAnyActiveAccessToken()
                        .orElseThrow(() -> new IllegalStateException("No valid broker token, login again"));
        return getCandleData(kiteCandleUrl, accessToken);
    }

    private String getCandleData(String kiteCandleUrl, String accessToken) throws Exception {
        return kiteApiClient.getData(kiteCandleUrl, accessToken);
    }

    // Kite historical path, e.g. /instruments/historical/738561/5minute?from=...&to=...&continuous=0&oi=0
    private static String getCandleUrl(long token, CandleRange range) {
        // The builder adds ? & / and encodes the values (e.g. the space in "2025-01-01 00:00:00")
        return UriComponentsBuilder.fromPath(UrlConstants.KITE_INSTRUMENTS_HISTORICAL_PATH)
                .pathSegment(String.valueOf(token), range.getInterval().getCode())
                .queryParam("from", range.getStartTime().format(KITE_PARAM_FORMAT))
                .queryParam("to", range.getEndTime().format(KITE_PARAM_FORMAT))
                .queryParam("continuous", range.isContinuous() ? 1 : 0)
                .queryParam("oi", range.isOi() ? 1 : 0)
                .encode()
                .toUriString();
    }

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
