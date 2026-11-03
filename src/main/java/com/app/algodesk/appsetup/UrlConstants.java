package com.app.algodesk.appsetup;

public class UrlConstants {

    public static final String KITE_LOGIN_URL = "https://kite.zerodha.com/connect/login?api_key=%s&v=3&redirect_params=%s";

    public static final String BASE_URL = "https://api.kite.trade";

    public static final String KITE_TOKEN_EXCHANGE_URL = "https://api.kite.trade/session/token";

    public static final String KITE_USER_PROFILE_URL = "https://api.kite.trade/user/profile";

    public static final String KITE_USER_PROFILE_PATH = "/user/profile";

    public static final String KITE_USER_MARGINS_PATH = "/user/margins";

    public static final String KITE_ORDERS_PATH = "/orders";

    public static final String KITE_HOLDINGS_PATH = "/portfolio/holdings";

    public static final String KITE_POSITIONS_PATH = "/portfolio/positions";

    public static final String KITE_SESSION_TOKEN_PATH = "/session/token";

    public static final String KITE_INSTRUMENTS_PATH = "/instruments";

    public static final String KITE_INSTRUMENTS_HISTORICAL_PATH = "/instruments/historical";

    // Last price + today's OHLC for up to 1000 instruments: /quote/ohlc?i=NSE:INFY&i=BSE:SENSEX
    public static final String KITE_QUOTE_OHLC_PATH = "/quote/ohlc";

}
