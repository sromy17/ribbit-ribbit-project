package com.neueda.leap.trading.api;

import java.util.List;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import com.neueda.leap.trading.service.impl.TicketService;
import com.neueda.leap.trading.service.impl.TicketService.Ticket;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

@RestController
public class TicketController {
    public record DisputeRequest(
        @NotBlank @Pattern(regexp = "Fraud|Accident|Incorrect transfer") String category,
        @NotBlank @Size(max = 75) String reason) {}
    public record SupportRequest(@NotBlank @Size(max = 75) String reason) {}
    public record StatusRequest(@NotBlank String status, @NotBlank String expectedStatus) {}

    private final TicketService tickets;

    public TicketController(TicketService tickets) {
        this.tickets = tickets;
    }

    @PostMapping("/accounts/{accountId}/orders/{orderId}/disputes")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Integer> dispute(@PathVariable int accountId, @PathVariable int orderId,
        @Valid @RequestBody DisputeRequest request, @AuthenticationPrincipal Jwt jwt) {
        return Map.of("ticketId", tickets.createDispute(jwt, accountId, orderId, request.category(), request.reason()));
    }

    @PostMapping("/accounts/{accountId}/support-tickets")
    @ResponseStatus(HttpStatus.CREATED)
    public Map<String, Integer> support(@PathVariable int accountId,
        @Valid @RequestBody SupportRequest request, @AuthenticationPrincipal Jwt jwt) {
        return Map.of("ticketId", tickets.createSupport(jwt, accountId, request.reason()));
    }

    @GetMapping("/accounts/{accountId}/tickets/{kind}")
    public List<Ticket> ownTickets(@PathVariable int accountId, @PathVariable String kind,
        @AuthenticationPrincipal Jwt jwt) {
        validateKind(kind);
        tickets.requireOwner(jwt, accountId);
        return tickets.list(kind, accountId);
    }

    @GetMapping("/admin/tickets/{kind}")
    public List<Ticket> allTickets(@PathVariable String kind) {
        validateKind(kind);
        return tickets.list(kind, null);
    }

    @PatchMapping("/admin/tickets/{kind}/{ticketId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void update(@PathVariable String kind, @PathVariable int ticketId,
        @Valid @RequestBody StatusRequest request, @AuthenticationPrincipal Jwt jwt) {
        validateKind(kind);
        tickets.update(kind, ticketId, request.status(), request.expectedStatus(), jwt);
    }

    private void validateKind(String kind) {
        if (!"dispute".equals(kind) && !"support".equals(kind)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unknown ticket kind");
        }
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> ticketError(ResponseStatusException error) {
        return ResponseEntity.status(error.getStatusCode()).body(
            Map.of("message", error.getReason() == null ? "Ticket request failed" : error.getReason()));
    }
}
