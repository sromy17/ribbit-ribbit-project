package com.neueda.leap.trading.api;

import com.neueda.leap.trading.service.impl.SupportTicketService;
import jakarta.validation.Valid;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class SupportTicketController {
    private final SupportTicketService service;
    public SupportTicketController(SupportTicketService service) { this.service=service; }

    @PostMapping("/accounts/{accountId}/support-tickets")
    public SupportTicketService.Ticket submit(@PathVariable int accountId,@Valid @RequestBody SupportTicketService.Submission request) {
        return service.submit(accountId,request);
    }
    @GetMapping("/accounts/{accountId}/support-tickets")
    public SupportTicketService.Page customer(@PathVariable int accountId,@RequestParam(defaultValue="0") int page) {
        return service.customerTickets(accountId,page);
    }
    @GetMapping("/api/admin/support-tickets")
    @PreAuthorize("hasAuthority('PERM_DISPUTE_READ')")
    public SupportTicketService.Page staff(@RequestParam(defaultValue="OPEN") String status,@RequestParam(defaultValue="0") int page) {
        return service.staffTickets(status,page);
    }
    @PostMapping("/api/admin/support-tickets/{ticketId}/status")
    @PreAuthorize("hasAuthority('PERM_DISPUTE_WORK')")
    public SupportTicketService.Ticket update(@PathVariable int ticketId,@Valid @RequestBody SupportTicketService.Transition request) {
        return service.update(ticketId,request);
    }
}
