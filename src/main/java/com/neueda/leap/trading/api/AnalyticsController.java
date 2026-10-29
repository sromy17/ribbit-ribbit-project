package com.neueda.leap.trading.api;

import com.neueda.leap.trading.analytics.*;
import java.time.LocalDate;
import java.util.Map;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/analytics")
@PreAuthorize("hasAuthority('PERM_REPORTING')")
public class AnalyticsController {
    private final AnalyticsService service;
    public AnalyticsController(AnalyticsService service) { this.service = service; }

    @GetMapping({"/overview", "/activity", "/instruments", "/client-segments"})
    public Map<String,Object> query(jakarta.servlet.http.HttpServletRequest request,
        @RequestParam LocalDate from, @RequestParam LocalDate to,
        @RequestParam(defaultValue="UTC") String timezone,
        @RequestParam(required=false) Integer instrumentId, @RequestParam(required=false) String side,
        @RequestParam(required=false) String segment, @RequestParam(defaultValue="0") int page,
        @RequestParam(defaultValue="25") int size, @RequestParam(defaultValue="fills") String sort,
        @RequestParam(defaultValue="desc") String direction, @RequestParam(required=false) String datasetId) {
        String kind = request.getRequestURI().substring(request.getRequestURI().lastIndexOf('/') + 1);
        return service.readPinned(kind, new AnalyticsQuery(from,to,timezone,instrumentId,side,segment,page,size,sort,direction),datasetId);
    }
    @GetMapping("/data-status") public Map<String,Object> status() { return service.status(); }
    @PostMapping("/reports") public Map<String,Object> issue(@Valid @RequestBody AnalyticsReportRequest request) { return service.issue(request); }
    @GetMapping("/reports/{reportId}") public Map<String,Object> report(@PathVariable String reportId) { return service.report(reportId); }
}
