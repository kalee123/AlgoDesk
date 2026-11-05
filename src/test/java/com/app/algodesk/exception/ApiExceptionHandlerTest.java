package com.app.algodesk.exception;

import com.app.algodesk.controller.InstrumentController;
import com.app.algodesk.models.CandleInfo;
import com.app.algodesk.service.CandleService;
import com.app.algodesk.service.InstrumentSearchService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

// Checks that exceptions thrown from a controller become the right status + {"error": ...} JSON.
// Standalone MockMvc: no Spring context, no DB, no Kite; the services are Mockito fakes.
class ApiExceptionHandlerTest {

    private CandleService candleService;
    private MockMvc mvc;

    @BeforeEach
    void setUp() {
        candleService = mock(CandleService.class);
        InstrumentController controller = new InstrumentController(mock(InstrumentSearchService.class), candleService);
        mvc = MockMvcBuilders.standaloneSetup(controller)
                .setControllerAdvice(new ApiExceptionHandler())
                .build();
    }

    @Test
    void successPassesThrough() throws Exception {
        when(candleService.getHistoricCandles(argThat(r -> r.getExchange().equals("NSE")
                && r.getSymbol().equals("RELIANCE") && r.getInterval().equals("day"))))
                .thenReturn(List.of(new CandleInfo(1, 100, 110, 90, 105, 500)));

        mvc.perform(get("/api/instruments/NSE/RELIANCE/candles"))
                .andExpect(status().isOk())
                .andExpect(content().json("[{\"time\":1,\"open\":100,\"high\":110,\"low\":90,\"close\":105,\"volume\":500}]"));
    }

    @Test
    void responseStatusExceptionKeepsItsStatusAndReason() throws Exception {
        when(candleService.getHistoricCandles(any()))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Unknown instrument NSE:NOPE"));

        mvc.perform(get("/api/instruments/NSE/NOPE/candles"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Unknown instrument NSE:NOPE"));
    }

    @Test
    void badDateParameterIs400() throws Exception {
        mvc.perform(get("/api/instruments/NSE/RELIANCE/candles?from=abc"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").exists());
    }

    @Test
    void anyOtherExceptionIs502WithoutLeakingDetails() throws Exception {
        when(candleService.getHistoricCandles(any()))
                .thenThrow(new IOException("connect timed out to api.kite.trade"));

        mvc.perform(get("/api/instruments/NSE/RELIANCE/candles"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Upstream request failed"));
    }
}
