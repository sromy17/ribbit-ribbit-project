package com.neueda.leap.trading.api;

import java.util.Map;

import org.springframework.http.HttpStatus;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.neueda.leap.etl.OlapEtlService;

@RestController
@RequestMapping({"/api/etl", "/etl"})
public class EtlController {
    private final OlapEtlService etlService;

    public EtlController(OlapEtlService etlService) {

        this.etlService = etlService;
    }

    @PostMapping("/run")
    @org.springframework.security.access.prepost.PreAuthorize("hasAuthority('PERM_ETL_TRIGGER')")
    public ResponseEntity<Map<String, Object>> runEtl() {
        OlapEtlService.EtlResult result = etlService.runNow();
        HttpStatus status = result.successful()
            ? HttpStatus.OK
            : HttpStatus.SERVICE_UNAVAILABLE;

        return ResponseEntity.status(status).body(Map.of(
            "success", result.successful(),
            "message", result.message()
        ));
    }
}

