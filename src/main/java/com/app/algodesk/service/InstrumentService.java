package com.app.algodesk.service;

import com.app.algodesk.repository.InstrumentRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

// Lookups on our instruments table (filled daily by InstrumentDataService). DB only: no Kite call,
// no request scope, so it works from any thread.
@Service
public class InstrumentService {

    private final InstrumentRepository instrumentRepository;

    public InstrumentService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

    // NSE + RELIANCE -> 738561. Kite APIs (historical candles, ticker) take this number, not the symbol.
    // The exchange is needed because the same symbol trades on both NSE and BSE with different tokens.
    public long getInstrumentToken(String exchange, String symbol) {
        return instrumentRepository.findByExchangeAndTradingsymbol(exchange.toUpperCase(), symbol.toUpperCase())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown instrument " + exchange + ":" + symbol))
                .getInstrumentToken();
    }
}
