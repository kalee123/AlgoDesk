package com.app.algodesk.service;

import com.app.algodesk.broker.kite.KiteTickerClient;
import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.List;

// Decides when live market data runs and what happens to each tick.
@Service
public class MarketDataService {

    private static final Logger log = LoggerFactory.getLogger(MarketDataService.class);

    // Hardcoded for now; later this comes from a watchlist + the instrument list
    private static final List<Long> WATCHLIST = List.of(
            256265L,   // NIFTY 50
            738561L    // RELIANCE (NSE)
    );

    private final KiteTickerClient kiteTickerClient;

    public MarketDataService(KiteTickerClient kiteTickerClient) {
        this.kiteTickerClient = kiteTickerClient;
    }

    public void start(String accessToken) {
        try {
            kiteTickerClient.start(accessToken, WATCHLIST, this::onTick);
        } catch (KiteException e) {
            // Live data is optional; the rest of the app keeps working without it
            // (KiteException extends Throwable, not Exception, so it needs its own catch)
            log.warn("Could not start live market data: {}", e.message);
        } catch (Exception e) {
            log.warn("Could not start live market data: {}", e.getMessage());
        }
    }

    public void stop() {
        kiteTickerClient.stop();
    }

    // Next step: feed these into the candle builder instead of just logging
    private void onTick(Tick tick) {
        log.info("{}  LTP {}  vol {}  at {}",
                tick.getInstrumentToken(), tick.getLastTradedPrice(),
                tick.getVolumeTradedToday(), tick.getTickTimestamp());
    }
}
