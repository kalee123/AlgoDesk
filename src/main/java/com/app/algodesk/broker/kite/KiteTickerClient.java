package com.app.algodesk.broker.kite;

import com.zerodhatech.kiteconnect.kitehttp.exceptions.KiteException;
import com.zerodhatech.models.Tick;
import com.zerodhatech.ticker.KiteTicker;
import com.zerodhatech.ticker.OnError;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

// Wraps Kite's WebSocket. One connection for the whole app; start() replaces any existing one.
@Component
public class KiteTickerClient {

    private static final Logger log = LoggerFactory.getLogger(KiteTickerClient.class);

    private final KiteProperties props;
    private KiteTicker ticker;

    public KiteTickerClient(KiteProperties props) {
        this.props = props;
    }

    public synchronized void start(String accessToken, List<Long> instrumentTokens, Consumer<Tick> onTick)
            throws KiteException {
        stop();

        KiteTicker newTicker = new KiteTicker(accessToken, props.getApiKey());
        ArrayList<Long> tokens = new ArrayList<>(instrumentTokens);

        newTicker.setOnConnectedListener(() -> {
            log.info("Ticker connected, subscribing to {}", tokens);
            newTicker.subscribe(tokens);
            newTicker.setMode(tokens, KiteTicker.modeFull);
        });
        newTicker.setOnDisconnectedListener(() -> log.info("Ticker disconnected"));
        newTicker.setOnTickerArrivalListener(ticks -> ticks.forEach(onTick));
        newTicker.setOnErrorListener(new OnError() {
            @Override
            public void onError(Exception e) {
                log.warn("Ticker error: {}", e.getMessage());
            }

            @Override
            public void onError(KiteException e) {
                log.warn("Ticker error: {}", e.message);
            }

            @Override
            public void onError(String error) {
                log.warn("Ticker error: {}", error);
            }
        });

        newTicker.setTryReconnection(true);
        newTicker.setMaximumRetries(10);
        newTicker.setMaximumRetryInterval(30);
        newTicker.connect();
        ticker = newTicker;
    }

    public synchronized void stop() {
        if (ticker == null) {
            return;
        }
        // Otherwise the SDK treats the disconnect as a drop and reconnects
        ticker.setTryReconnection(false);
        ticker.disconnect();
        ticker = null;
    }
}
