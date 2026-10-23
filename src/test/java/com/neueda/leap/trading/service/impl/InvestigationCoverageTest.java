package com.neueda.leap.trading.service.impl;

import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class InvestigationCoverageTest {
    @Test void nonemptyTimelineDoesNotProveAnExecutedOrderIsComplete() {
        var result=InvestigationCoverage.assess(Map.of("status","EXECUTED"),List.of(Map.of("event_type","ORDER_SUBMITTED","event_sequence",1)),List.of(),List.of(),List.of());
        assertEquals("INCOMPLETE",result.get("status"));assertTrue(result.get("warnings").toString().contains("no fill evidence"));
    }
    @Test void detectsSequenceAndQuoteGapsWithoutInventingThem() {
        var events=List.of(Map.<String,Object>of("event_type","ORDER_SUBMITTED","event_sequence",1),Map.<String,Object>of("event_type","PRICING_DECISION","event_sequence",3));
        var result=InvestigationCoverage.assess(Map.of("status","PENDING"),events,List.of(),List.of(),List.of());
        assertTrue(result.get("warnings").toString().contains("sequence"));assertTrue(result.get("warnings").toString().contains("quote provenance"));
    }
    @Test void pendingAttemptIsNotReportedAsSuccessOrTerminalRejection() {
        var result=InvestigationCoverage.assess(Map.of("status","PENDING"),List.of(Map.of("event_type","EXECUTION_ATTEMPTED","event_sequence",1)),List.of(),List.of(),List.of());
        assertTrue(result.get("warnings").toString().contains("final outcome is not recorded"));
    }
}
