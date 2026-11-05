package com.app.algodesk.service;

import com.app.algodesk.appsetup.UrlConstants;
import com.app.algodesk.entity.InstrumentEntity;
import com.app.algodesk.repository.InstrumentRepository;
import org.json.JSONArray;
import org.json.JSONObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.util.List;

// Instrument search for the Market Watch page. Reads only from our instruments table, never from Kite.
@Service
public class InstrumentSearchService {

    private static final int MIN_QUERY_LENGTH = 2;
    private static final int MAX_RESULTS = 20;

    private final InstrumentRepository instrumentRepository;

    public InstrumentSearchService(InstrumentRepository instrumentRepository) {
        this.instrumentRepository = instrumentRepository;
    }

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
