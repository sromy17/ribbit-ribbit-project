package com.neueda.leap.trading.api;

import java.util.List;
import com.neueda.leap.trading.domain.DisputeCase;
import com.neueda.leap.trading.service.impl.DisputeCaseService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
public class DisputeCaseController {
    private final DisputeCaseService service;
    public DisputeCaseController(DisputeCaseService service) { this.service = service; }

    @PostMapping("/disputes")
    public DisputeCase submit(@Valid @RequestBody DisputeSubmissionRequest request) { return service.submit(request); }

    @GetMapping("/api/admin/disputes")
    @PreAuthorize("hasAuthority('PERM_DISPUTE_READ')")
    public List<DisputeCase> list(@RequestParam(required = false) String status) { return service.list(status); }

    @GetMapping("/api/admin/disputes/page")
    @PreAuthorize("hasAuthority('PERM_DISPUTE_READ')")
    public java.util.Map<String, Object> page(@RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return service.page(status, page, size);
    }

    @GetMapping("/api/admin/disputes/{caseId}")
    @PreAuthorize("hasAuthority('PERM_DISPUTE_READ')")
    public DisputeCase get(@PathVariable Long caseId) { return service.get(caseId); }

    @GetMapping("/api/admin/disputes/{caseId}/events")
    @PreAuthorize("hasAuthority('PERM_DISPUTE_READ')")
    public java.util.List<java.util.Map<String, Object>> events(@PathVariable Long caseId) { return service.events(caseId); }

    @PostMapping("/api/admin/disputes/{caseId}/claim")
    @ResponseStatus(HttpStatus.OK) @PreAuthorize("hasAuthority('PERM_DISPUTE_WORK')")
    public DisputeCase claim(@PathVariable Long caseId, @Valid @RequestBody DisputeTransitionRequest request) { return service.claim(caseId, request); }

    @PostMapping("/api/admin/disputes/{caseId}/accept")
    @PreAuthorize("hasAuthority('PERM_DISPUTE_WORK')")
    public DisputeCase accept(@PathVariable Long caseId, @Valid @RequestBody DisputeTransitionRequest request) { return service.accept(caseId, request); }

    @PostMapping("/api/admin/disputes/{caseId}/notes")
    @PreAuthorize("hasAuthority('PERM_DISPUTE_WORK')")
    public DisputeCase note(@PathVariable Long caseId, @Valid @RequestBody DisputeNoteRequest request) { return service.note(caseId, request); }

    @PostMapping("/api/admin/disputes/{caseId}/resolve")
    @PreAuthorize("hasAuthority('PERM_DISPUTE_WORK')")
    public DisputeCase resolve(@PathVariable Long caseId, @Valid @RequestBody DisputeTransitionRequest request) { return service.resolve(caseId, request); }

    @PostMapping("/api/admin/disputes/{caseId}/release")
    @PreAuthorize("hasAnyAuthority('PERM_DISPUTE_WORK','PERM_DISPUTE_SUPERVISOR')")
    public DisputeCase release(@PathVariable Long caseId, @Valid @RequestBody DisputeTransitionRequest request) { return service.release(caseId, request); }

    @PostMapping("/api/admin/disputes/{caseId}/reassign")
    @PreAuthorize("hasAuthority('PERM_DISPUTE_SUPERVISOR')")
    public DisputeCase reassign(@PathVariable Long caseId, @Valid @RequestBody DisputeTransitionRequest request) { return service.reassign(caseId, request); }
}