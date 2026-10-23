package com.neueda.leap.trading.service.impl;

import java.util.*;

/** Reports detectable evidence gaps without inventing an old balance, quote or execution outcome. */
public final class InvestigationCoverage {
    private InvestigationCoverage() { }
    public static Map<String,Object> assess(Map<String,Object> order,List<Map<String,Object>> events,
        List<Map<String,Object>> fills,List<Map<String,Object>> cash,List<Map<String,Object>> holdings) {
        var warnings=new LinkedHashSet<String>();
        var types=events.stream().map(e->e.get("event_type")).toList();
        if(!types.contains("ORDER_SUBMITTED")) warnings.add("Submission evidence is missing.");
        String status=String.valueOf(order.get("status"));
        if((status.equals("PENDING") || !fills.isEmpty()) && !types.contains("ORDER_ACCEPTED")) warnings.add("Acceptance evidence is missing.");
        long expected=1;
        for(var event:events) {
            if(!(event.get("event_sequence") instanceof Number sequence) || sequence.longValue()!=expected) warnings.add("Lifecycle sequence contains gaps or inconsistent ordering.");
            expected=event.get("event_sequence") instanceof Number sequence ? sequence.longValue()+1 : expected+1;
            if("PRICING_DECISION".equals(event.get("event_type")) && (event.get("quote_source")==null || event.get("quote_at_utc")==null || event.get("quote_currency")==null))
                warnings.add("Upstream quote provenance, time or currency is incomplete.");
        }
        if(status.equals("EXECUTED") && fills.isEmpty()) warnings.add("Order says EXECUTED but no fill evidence is recorded.");
        if(!fills.isEmpty()) {
            if(!types.contains("PRICING_DECISION")) warnings.add("Pricing-decision evidence is missing.");
            if(!types.contains("FILL_COMMITTED")) warnings.add("Fill lifecycle evidence is missing.");
            if(cash.isEmpty()) warnings.add("Recorded fills have no cash-movement evidence.");
            if(holdings.isEmpty()) warnings.add("Recorded fills have no holding-movement evidence.");
            if(fills.stream().anyMatch(f->f.get("executed_at_utc")==null || f.get("execution_currency")==null || f.get("pricing_event_id")==null))
                warnings.add("Fill time, currency or pricing-event attribution is incomplete.");
        }
        if(cash.stream().anyMatch(c->c.get("currency")==null || c.get("currency_source")==null)) warnings.add("Cash currency is unavailable or unverified.");
        long lastAttempt=0,lastOutcome=0;
        for(var event:events) {
            long sequence=event.get("event_sequence") instanceof Number n ? n.longValue() : 0;
            if("EXECUTION_ATTEMPTED".equals(event.get("event_type"))) lastAttempt=Math.max(lastAttempt,sequence);
            if(Set.of("EXECUTION_FAILED","FILL_COMMITTED").contains(String.valueOf(event.get("event_type")))) lastOutcome=Math.max(lastOutcome,sequence);
        }
        if(lastAttempt>lastOutcome && status.equals("PENDING")) warnings.add("An execution was attempted; its final outcome is not recorded. Do not infer success or a rejection reason.");
        return Map.of("status",warnings.isEmpty()?"NO_DETECTED_GAPS":"INCOMPLETE","warnings",List.copyOf(warnings),
            "scope","Recorded evidence only; this does not certify unrecorded history or reconstruct a missing historical balance.");
    }
}
