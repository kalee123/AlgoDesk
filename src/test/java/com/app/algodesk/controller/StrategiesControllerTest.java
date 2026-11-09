package com.app.algodesk.controller;

import com.app.algodesk.backtest.StrategyFactory;
import com.app.algodesk.backtest.TradingStrategy;
import com.app.algodesk.exception.ApiExceptionHandler;
import com.app.algodesk.models.BacktestResult;
import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.models.HistoricalDataRequest;
import com.app.algodesk.models.TradeDetails;
import com.app.algodesk.service.StrategyService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Checks the list endpoint, that a JSON body is bound onto HistoricalDataRequest (dates included), and bad JSON is a 400.
class StrategiesControllerTest {

    // A stand-in strategy: the factory is built from a plain map, exactly as Spring would inject it
    private static TradingStrategy fake(String name) {
        return new TradingStrategy() {
            @Override public String name() { return name; }
            @Override public String description() { return name + " rules"; }
            @Override public List<TradeDetails> run(List<CandleInfo> candles) { return List.of(); }
        };
    }

    private StrategyService strategyService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        strategyService = mock(StrategyService.class);
        StrategyFactory factory = new StrategyFactory(Map.of("pdb", fake("Previous Day"), "orb", fake("Opening Range")));
        mvc = MockMvcBuilders.standaloneSetup(new StrategiesController(factory, strategyService))
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void listsEveryStrategySortedByCode() throws Exception {
        mvc.perform(get("/api/strategies"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].code").value("orb"))
                .andExpect(jsonPath("$[0].name").value("Opening Range"))
                .andExpect(jsonPath("$[0].description").value("Opening Range rules"))
                .andExpect(jsonPath("$[1].code").value("pdb"));
    }

    @Test
    void jsonBodyIsBoundOntoTheRequest() throws Exception {
        when(strategyService.runStrategy(any(), eq("orb"))).thenReturn(new BacktestResult(List.of(), List.of()));

        mvc.perform(post("/api/strategies/orb/backtest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"exchange\":\"NSE\",\"symbol\":\"RELIANCE\",\"interval\":\"5minute\","
                                + "\"from\":\"2025-01-01\",\"to\":\"2025-03-31\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.summary.trades").value(0));

        ArgumentCaptor<HistoricalDataRequest> captor = ArgumentCaptor.forClass(HistoricalDataRequest.class);
        verify(strategyService).runStrategy(captor.capture(), eq("orb"));
        HistoricalDataRequest r = captor.getValue();
        assertEquals("NSE", r.getExchange());
        assertEquals("RELIANCE", r.getSymbol());
        assertEquals("5minute", r.getInterval());
        assertEquals(LocalDate.of(2025, 1, 1), r.getFrom());
        assertEquals(LocalDate.of(2025, 3, 31), r.getTo());
    }

    @Test
    void unreadableBodyIs400() throws Exception {
        mvc.perform(post("/api/strategies/orb/backtest")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"from\":\"yesterday\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }
}
