package com.neueda.leap.trading.api;

import java.util.Map;
import org.springframework.dao.*;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.core.annotation.Order;

@RestControllerAdvice(assignableTypes=AnalyticsController.class)
@Order(0)
public class AnalyticsExceptionHandler {
    @ExceptionHandler({DataIntegrityViolationException.class,ConcurrencyFailureException.class})
    public ResponseEntity<Map<String,String>> conflict(DataAccessException e) {
        return ResponseEntity.status(409).body(Map.of("message","Report request conflicts with an existing version; retry or retrieve the saved report"));
    }
    @ExceptionHandler({DataAccessException.class,org.springframework.transaction.TransactionException.class})
    public ResponseEntity<Map<String,String>> unavailable(RuntimeException e) {
        return ResponseEntity.status(503).body(Map.of("message","Analytics is temporarily unavailable. Trading remains separate; retry later."));
    }
}
