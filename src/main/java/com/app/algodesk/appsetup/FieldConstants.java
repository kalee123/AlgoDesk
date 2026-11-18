package com.app.algodesk.appsetup;

import java.time.ZoneId;
import java.time.format.DateTimeFormatter;

public class FieldConstants {

    public static final String FIELD_KIETE_STATE = "kite_state";

    public static final String FIELD_USER_AUTH = "USER_AUTH";

    public static final String FIELD_USER_ID = "USER_ID";

    public static final String FIELD_TOKEN_EXPIRES_AT = "TOKEN_EXPIRES_AT";

    public static final ZoneId IST = ZoneId.of("Asia/Kolkata");

    public static final int TOTAL_MARKET_MINUTES_PER_DAY = 375; // NSE 09:15 - 15:30

    public static final DateTimeFormatter KITE_PARAM_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    public static final DateTimeFormatter KITE_CANDLE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ssZ");

}