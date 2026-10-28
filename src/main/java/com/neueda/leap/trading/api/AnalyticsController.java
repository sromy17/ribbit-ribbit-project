package com.neueda.leap.trading.api;

import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import com.neueda.leap.trading.service.impl.AnalyticsService;
import com.neueda.leap.trading.service.impl.AnalyticsInsightsService.Period;

@RestController
@RequestMapping("/analyst")
public class AnalyticsController {
    private static final Logger log = LoggerFactory.getLogger(AnalyticsController.class);
    private final AnalyticsService analytics;

    public AnalyticsController(AnalyticsService analytics) {
        this.analytics = analytics;
    }

    @GetMapping("/overview")
    public AnalyticsService.Report overview(@RequestParam(defaultValue = "ALL") Period period) {
        return period == Period.ALL ? analytics.report() : analytics.report(period);
    }

    @ExceptionHandler(DataAccessException.class)
    public ResponseEntity<Map<String, String>> unavailable(DataAccessException error) {
        log.error("Unable to read OLAP analytics", error);
        return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
            .body(Map.of("message", "Reporting data is unavailable. Check the OLAP database and ETL logs."));
    }
}
