package com.app.algodesk.service;

import com.app.algodesk.appsetup.Intervals;
import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.HistoricalDataRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;

import static com.app.algodesk.appsetup.FieldConstants.IST;
import static com.app.algodesk.appsetup.FieldConstants.TOTAL_MARKET_MINUTES_PER_DAY;

// Our rules for historical candles: which instrument, which interval, which dates, how many days.
// How to get them from Kite (chunks, pacing, URL, JSON) is KiteCandleHandler's job.
@Service
public class CandleService {

    private static final Logger log = LoggerFactory.getLogger(CandleService.class);
    private static final int TARGET_CANDLES = 500;   // default range when no 'from' is given

    private final InstrumentService instrumentService;
    private final KiteCandleHandler kiteCandleHandler;

    public CandleService(InstrumentService instrumentService, KiteCandleHandler kiteCandleHandler) {
        this.instrumentService = instrumentService;
        this.kiteCandleHandler = kiteCandleHandler;
    }

    // Chart page: the browser draws every candle, so keep it to one Kite call (interval's maxDays)
    public List<CandleInfo> getChartCandles(HistoricalDataRequest request) throws Exception {
        return fetchCandles(request, false);
    }

    // Backtests: candles stay on the server, so allow long ranges (maxTotalDays), fetched in chunks
    public List<CandleInfo> getBacktestCandles(HistoricalDataRequest request) throws Exception {
        return fetchCandles(request, true);
    }

    private List<CandleInfo> fetchCandles(HistoricalDataRequest request, boolean forBacktest) throws Exception {
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

        // The only difference between chart and backtest: how many days we allow in total
        int allowedDays = forBacktest ? timeFrame.getMaxTotalDays() : timeFrame.getMaxDays();
        if (!checkValidRange(allowedDays, startDate, endDate)) {
            // Too long: keep the most recent days (recent data matters most)
            LocalDate trimmedStart = endDate.minusDays(allowedDays - 1);
            log.warn("Range {} to {} too long for {}, trimmed to start at {}",
                    startDate, endDate, timeFrame.getCode(), trimmedStart);
            startDate = trimmedStart;
        }

        return kiteCandleHandler.fetchCandles(instrumentToken, timeFrame, startDate, endDate,
                request.isContinuous(), request.isOi());
    }

    // allowedDays: maxDays for the chart, maxTotalDays for backtests (both per interval, see Intervals)
    private static boolean checkValidRange(int allowedDays, LocalDate startDate, LocalDate endDate) {
        long totalDays = ChronoUnit.DAYS.between(startDate, endDate) + 1;   // both ends included
        return totalDays <= allowedDays;
    }

    // e.g. 5minute: 375 / 5 = 75 candles a day -> 500 / 75 = 7 trading days -> ~10 calendar days (+ weekends)
    private static long daysFor500Candles(Intervals interval) {
        double candlesPerDay = (double) TOTAL_MARKET_MINUTES_PER_DAY / interval.getMinutesPerCandle();
        long tradingDays = (long) Math.ceil(TARGET_CANDLES / candlesPerDay);
        long calendarDays = tradingDays * 7 / 5 + 3;   // weekends, plus a little slack for holidays
        return Math.min(calendarDays, interval.getMaxDays());
    }
}
