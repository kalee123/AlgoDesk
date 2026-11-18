package com.app.algodesk.models;

import com.app.algodesk.appsetup.Intervals;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;

import static com.app.algodesk.appsetup.FieldConstants.IST;
import static com.app.algodesk.appsetup.FieldConstants.TOTAL_MARKET_MINUTES_PER_DAY;

// A HistoricalDataRequest after checking and filling defaults: a valid interval and exact start/end times.
// Built once at the start (CandleQuery.from), so the rest of the code never sees nulls or bad values.
public record CandleQuery(
        Intervals interval,
        LocalDateTime startTime,
        LocalDateTime endTime,
        boolean defaultRange,   // true when the caller gave no 'from', so we picked a range for ~500 candles
        boolean continuous,
        boolean oi) {

    public static final int TARGET_CANDLES = 500;

    public static CandleQuery from(HistoricalDataRequest request) {
        Intervals interval = Intervals.fromCode(request.getInterval())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Unknown interval '" + request.getInterval() + "'. Use one of " + Intervals.codes()));

        // Whole days: 'to' runs to the end of its day, 'from' starts at midnight
        boolean defaultRange = request.getFrom() == null;
        LocalDate endDate = request.getTo() != null ? request.getTo() : LocalDate.now(IST);
        LocalDate startDate = defaultRange ? endDate.minusDays(daysFor500Candles(interval)) : request.getFrom();
        LocalDateTime startTime = startDate.atStartOfDay();
        LocalDateTime endTime = endDate.atTime(23, 59, 59);

        if (startTime.isAfter(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'from' must not be after 'to'");
        }
        if (startTime.plusDays(interval.getMaxDays()).isBefore(endTime)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Range too long for " + interval.getCode()
                    + ": Kite allows at most " + interval.getMaxDays() + " days per request");
        }
        return new CandleQuery(interval, startTime, endTime, defaultRange, request.isContinuous(), request.isOi());
    }

    // e.g. 5minute: 375 / 5 = 75 candles a day -> 500 / 75 = 7 trading days -> ~10 calendar days (+ weekends)
    private static long daysFor500Candles(Intervals interval) {
        double candlesPerDay = (double) TOTAL_MARKET_MINUTES_PER_DAY / interval.getMinutesPerCandle();
        long tradingDays = (long) Math.ceil(TARGET_CANDLES / candlesPerDay);
        long calendarDays = tradingDays * 7 / 5 + 3;   // weekends, plus a little slack for holidays
        return Math.min(calendarDays, interval.getMaxDays());
    }
}
