package com.app.algodesk.appsetup;

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

import static com.app.algodesk.appsetup.FieldConstants.TOTAL_MARKET_MINUTES_PER_DAY;

// Candle intervals Kite supports, with the longest range Kite returns in one historical request.
public enum Intervals {

    MINUTE("minute", 1, 60),
    THREE_MINUTE("3minute", 3, 100),
    FIVE_MINUTE("5minute", 5, 100),
    TEN_MINUTE("10minute", 10, 100),
    FIFTEEN_MINUTE("15minute", 15, 200),
    THIRTY_MINUTE("30minute", 30, 200),
    SIXTY_MINUTE("60minute", 60, 400),
    DAY("day", TOTAL_MARKET_MINUTES_PER_DAY, 2000);

    private final String code;          // what Kite and the browser use, e.g. "5minute"
    private final int minutesPerCandle;
    private final int maxDays;          // max days Kite allows in one request

    Intervals(String code, int minutesPerCandle, int maxDays) {
        this.code = code;
        this.minutesPerCandle = minutesPerCandle;
        this.maxDays = maxDays;
    }

    public String getCode() {
        return code;
    }

    public int getMinutesPerCandle() {
        return minutesPerCandle;
    }

    public int getMaxDays() {
        return maxDays;
    }

    // "5minute" -> FIVE_MINUTE; empty when Kite has no such interval
    public static Optional<Intervals> fromCode(String code) {
        return Arrays.stream(values())
                .filter(i -> i.code.equals(code))
                .findFirst();
    }

    // ["minute", "3minute", ...] for error messages
    public static List<String> codes() {
        return Arrays.stream(values()).map(Intervals::getCode).toList();
    }
}
