package com.app.algodesk.service;

import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.HistoricalDataRequest;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class StrategyBackTester {

    private final CandleService candleService;

    public StrategyBackTester(CandleService candleService) {
        this.candleService = candleService;
    }

    public void backTest(HistoricalDataRequest request, String strategy) throws Exception {
        List<CandleInfo> candles = candleService.getHistoricCandles(request);



    }
}
