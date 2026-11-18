package com.app.algodesk.service;

import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.authentication.RqInfo;
import com.app.algodesk.authentication.UserAuthService;
import com.app.algodesk.broker.kite.KiteApiClient;
import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.CandleQuery;
import com.app.algodesk.models.HistoricalDataRequest;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import static com.app.algodesk.appsetup.FieldConstants.KITE_CANDLE_FORMAT;
import static com.app.algodesk.appsetup.FieldConstants.KITE_PARAM_FORMAT;

@Service
public class CandleService {

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

    // Candles oldest first, time in epoch seconds (what chart libraries expect); oi is set only when request.isOi().
    // from/to are optional: when missing, the range is chosen to give about 500 candles for the interval.
    public List<CandleInfo> getHistoricCandles(HistoricalDataRequest request) throws Exception {
        CandleQuery query = CandleQuery.from(request);   // 400 on a bad interval or range, before any I/O
        long instrumentToken = instrumentService.getInstrumentToken(request.getExchange(), request.getSymbol());
        String kiteCandleUrl = getCandleUrl(instrumentToken, query);

        String candleData = getCandleData(kiteCandleUrl);
        List<CandleInfo> candles = getParsedCandle(candleData, query.oi());

        // The day-count estimate is rough (holidays, MCX's longer hours), so only keep the latest 500
        // when the range was chosen by us; an explicit from/to gets everything in it.
        if (query.defaultRange() && candles.size() > CandleQuery.TARGET_CANDLES) {
            candles = new ArrayList<>(candles.subList(candles.size() - CandleQuery.TARGET_CANDLES, candles.size()));
        }
        return candles;
    }

    // Inside an HTTP request: the logged-in user's token (RqInfo).
    // On any other thread (strategy threads, scheduler) there is no request, and touching RqInfo throws
    // "Scope 'request' is not active" instead of returning null, so check for a request first
    // and fall back to the DB. Same idea as InstrumentScheduler; fine for a single-user app.
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
    private static String getCandleUrl(long token, CandleQuery query) {
        // The builder adds ? & / and encodes the values (e.g. the space in "2025-01-01 00:00:00")
        return UriComponentsBuilder.fromPath(UrlConstants.KITE_INSTRUMENTS_HISTORICAL_PATH)
                .pathSegment(String.valueOf(token), query.interval().getCode())
                .queryParam("from", query.startTime().format(KITE_PARAM_FORMAT))
                .queryParam("to", query.endTime().format(KITE_PARAM_FORMAT))
                .queryParam("continuous", query.continuous() ? 1 : 0)
                .queryParam("oi", query.oi() ? 1 : 0)
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

//    public LinkedHashMap<String, List<CandleInfo>> getCandlesByDate(List<CandleInfo> candles) {
//        LinkedHashMap<String, List<CandleInfo>> candlesByDate = new LinkedHashMap<>();
//        for (CandleInfo candle : candles) {
//            String date = Instant.ofEpochSecond(candle.getTime()).atZone(IST).toLocalDate().toString();
//            candlesByDate.computeIfAbsent(date, d -> new ArrayList<>()).add(candle);
//        }
//        return candlesByDate;
//    }
}
