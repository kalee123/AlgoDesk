package com.app.algodesk.exception;

import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;
import org.springframework.web.server.ResponseStatusException;

// One place that turns exceptions thrown by any @RestController into a JSON error response,
// so controller methods only handle the success case (no try/catch in each one).
// Spring picks the most specific matching @ExceptionHandler for the thrown exception.
// Note: 401 for a missing login comes from SessionAuthFilter, which runs before controllers.
@RestControllerAdvice
public class ApiExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApiExceptionHandler.class);

    // Thrown on purpose by services for the caller's mistakes, e.g. unknown instrument (404), bad interval (400)
    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<String> handleStatus(ResponseStatusException e) {
        return error(e.getStatusCode().value(), e.getReason());
    }

    // Thrown by Spring itself for bad request values, e.g. ?from=abc for a LocalDate or a missing required param
    @ExceptionHandler({MethodArgumentTypeMismatchException.class, MissingServletRequestParameterException.class})
    public ResponseEntity<String> handleBadInput(Exception e) {
        return error(400, e.getMessage());
    }

    // Everything else: Kite down, timeout, or a bug. Log the full stack trace; don't leak details to the browser.
    @ExceptionHandler(Exception.class)
    public ResponseEntity<String> handleOther(Exception e) {
        log.error("Request failed", e);
        return error(502, "Upstream request failed");
    }

    private static ResponseEntity<String> error(int status, String message) {
        return ResponseEntity.status(status)
                .contentType(MediaType.APPLICATION_JSON)
                .body(new JSONObject().put("error", message).toString());
    }
}
