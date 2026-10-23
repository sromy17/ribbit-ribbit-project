package com.neueda.leap.trading.api;

import java.util.Map;

import com.neueda.leap.trading.service.impl.OltpToOlapEtlService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/etl")
public class EtlController {
    private final OltpToOlapEtlService etlService;

    public EtlController(OltpToOlapEtlService etlService) {
        this.etlService = etlService;
    }

    @PostMapping("/run")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERM_ETL_TRIGGER')")
    public ResponseEntity<Map<String, String>> run() {
        String runId = etlService.runFullLoad();
        return ResponseEntity.ok(Map.of("status", "completed", "runId", runId));
    }
}