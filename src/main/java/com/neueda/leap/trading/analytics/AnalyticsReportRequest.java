package com.neueda.leap.trading.analytics;

import java.time.LocalDate;
import jakarta.validation.constraints.*;

public record AnalyticsReportRequest(@NotNull LocalDate from, @NotNull LocalDate to,
    @NotBlank String timezone, Integer instrumentId, String side, String segment,
    @NotBlank @Size(max=100) String idempotencyKey, String amendsReportId,
    @Size(max=500) String amendmentReason, @Size(max=36) String datasetId) {
    public AnalyticsReportRequest(LocalDate from,LocalDate to,String timezone,Integer instrumentId,String side,String segment,
        String idempotencyKey,String amendsReportId,String amendmentReason) {
        this(from,to,timezone,instrumentId,side,segment,idempotencyKey,amendsReportId,amendmentReason,null);
    }
    public AnalyticsQuery query() { return new AnalyticsQuery(from, to, timezone, instrumentId, side, segment, 0, 100, "fills", "desc"); }
}
