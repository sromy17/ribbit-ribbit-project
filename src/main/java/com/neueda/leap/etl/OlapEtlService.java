package com.neueda.leap.etl;

import com.neueda.leap.trading.service.impl.OltpToOlapEtlService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/** Preserves merged startup/API settings while publishing the canonical analytics dataset. */
@Service
public class OlapEtlService {
    private static final Logger log = LoggerFactory.getLogger(OlapEtlService.class);
    private final OltpToOlapEtlService publisher;
    private final boolean enabled;
    private final String cron;

    public OlapEtlService(OltpToOlapEtlService publisher,
            @Value("${olap.enabled:false}") boolean enabled,
            @Value("${etl.schedule.cron:-}") String cron) {
        this.publisher = publisher;
        this.enabled = enabled;
        this.cron = cron;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void runOnStartup() { if (enabled) runNow(); }

    @Scheduled(fixedDelayString = "${olap.etl.fixed-delay-ms:300000}", initialDelayString = "${olap.etl.fixed-delay-ms:300000}")
    public void runScheduled() { if (enabled && "-".equals(cron)) runNow(); }

    public EtlResult runNow() {
        try {
            return new EtlResult(true, "Reporting dataset published", publisher.runFullLoad());
        } catch (RuntimeException failure) {
            log.error("Reporting refresh failed; retaining last published dataset", failure);
            return new EtlResult(false, "Reporting refresh failed; the last published dataset is retained", "");
        }
    }

    public record EtlResult(boolean successful, String message, String runId) { }
}
