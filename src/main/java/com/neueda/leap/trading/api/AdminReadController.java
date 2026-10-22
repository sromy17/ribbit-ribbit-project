package com.neueda.leap.trading.api;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.neueda.leap.trading.service.impl.AdminReadService;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin")
public class AdminReadController {
    private final AdminReadService service;

    public AdminReadController(AdminReadService service) { this.service = service; }

    @GetMapping("/orders")
    @PreAuthorize("hasAuthority('PERM_ORDER_INVESTIGATION')")
    public Map<String, Object> orders(
        @RequestParam(required = false) String status,
        @RequestParam(required = false) String symbol,
        @RequestParam(required = false) Integer accountId,
        @RequestParam LocalDate from,
        @RequestParam LocalDate to,
        @RequestParam(defaultValue = "0") int page,
        @RequestParam(defaultValue = "25") int size,
        @RequestParam(defaultValue = "created_at_utc") String sort,
        @RequestParam(defaultValue = "desc") String direction
    ) { return service.orders(status, symbol, accountId, from, to, page, size, sort, direction); }

    @GetMapping("/orders/{orderId}")
    @PreAuthorize("hasAuthority('PERM_ORDER_INVESTIGATION')")
    public Map<String, Object> order(@PathVariable Integer orderId) { return service.order(orderId); }

    @GetMapping("/orders/{orderId}/events")
    @PreAuthorize("hasAuthority('PERM_ORDER_INVESTIGATION')")
    public List<Map<String, Object>> events(@PathVariable Integer orderId) { return service.events(orderId); }

    @GetMapping("/orders/{orderId}/fills")
    @PreAuthorize("hasAuthority('PERM_ORDER_INVESTIGATION')")
    public List<Map<String, Object>> fills(@PathVariable Integer orderId) { return service.fills(orderId); }

    @GetMapping("/accounts/{accountId}/summary")
    @PreAuthorize("hasAuthority('PERM_ACCOUNT_READ')")
    public Map<String, Object> account(@PathVariable Integer accountId) { return service.accountSummary(accountId); }

    @GetMapping("/reports/trading-activity")
    @PreAuthorize("hasAuthority('PERM_REPORTING')")
    public Map<String, Object> activity(@RequestParam LocalDate from, @RequestParam LocalDate to,
                                        @RequestParam(required = false) String currency) {
        return service.activity(from, to, currency);
    }

    @GetMapping("/reporting/status")
    @PreAuthorize("hasAuthority('PERM_REPORTING')")
    public Map<String, Object> reportingStatus() { return service.reportingStatus(); }
}