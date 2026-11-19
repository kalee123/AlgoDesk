package com.app.algodesk.service;

import com.app.algodesk.entity.InstrumentEntity;
import com.app.algodesk.repository.InstrumentRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

// Reads on our instruments table (filled daily by InstrumentDataService). DB only: no Kite call,
// no request scope, so it works from any thread.
@Service
public class InstrumentService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_RESULTS = 20;

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

    // Search box on the Market Watch page
    public String search(String query) {
        // % and _ are LIKE wildcards; drop them so the user's text is matched literally
        String q = query == null ? "" : query.trim().toUpperCase().replaceAll("[%_]", "");
        if (q.length() < MIN_QUERY_LENGTH) {
            return "[]";
        }

        List<InstrumentEntity> found = instrumentRepository.search(q, PageRequest.of(0, MAX_RESULTS));

        // instrument_token is left out on purpose: the browser identifies instruments by exchange + symbol
        JSONArray result = new JSONArray();
        for (InstrumentEntity i : found) {
            result.put(new JSONObject()
                    .put("exchange", i.getExchange())
                    .put("tradingsymbol", i.getTradingsymbol())
                    .put("name", i.getName() == null ? JSONObject.NULL : i.getName())
                    .put("instrument_type", "INDICES".equals(i.getSegment()) ? "INDEX" : i.getInstrumentType())
                    .put("segment", i.getSegment())
                    .put("expiry", i.getExpiry() == null ? JSONObject.NULL : i.getExpiry().toString())
                    .put("strike", i.getStrike() == null ? JSONObject.NULL : i.getStrike())
                    .put("lot_size", i.getLotSize() == null ? JSONObject.NULL : i.getLotSize()));
        }
        return result.toString();
    }
}
