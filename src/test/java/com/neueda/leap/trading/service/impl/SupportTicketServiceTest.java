package com.neueda.leap.trading.service.impl;

import com.neueda.leap.trading.security.AccountAccessService;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

class SupportTicketServiceTest {
    final JdbcTemplate db = mock(JdbcTemplate.class);
    final AccountAccessService access = mock(AccountAccessService.class);
    final SupportTicketService service = new SupportTicketService(db, access);
    final UUID requestId = UUID.randomUUID();
    final SupportTicketService.Submission request = new SupportTicketService.Submission("PLATFORM_QUESTION", " Theme ", " How do I change theme? ", requestId);
    final SupportTicketService.Ticket saved = new SupportTicketService.Ticket(81,101,"Theme","PLATFORM_QUESTION","How do I change theme?","OPEN",Instant.now(),null,null,0);

    @Test
    void verifiesAccountOwnershipBeforeReadingOrCreatingTickets() {
        doThrow(new ResponseStatusException(HttpStatus.FORBIDDEN)).when(access).requireAccountOwner(102);
        assertThrows(ResponseStatusException.class, () -> service.submit(102, request));
        assertThrows(ResponseStatusException.class, () -> service.customerTickets(102, 0));
        verifyNoInteractions(db);
    }

    @Test
    @SuppressWarnings("unchecked")
    void safeReplayReturnsOriginalTicketAndRejectsChangedPayload() {
        when(db.queryForObject(anyString(),any(RowMapper.class),eq(requestId))).thenReturn(saved);
        assertEquals(saved, service.submit(101,request));
        assertEquals(saved, service.submit(101,request));
        var conflict = assertThrows(ResponseStatusException.class, () -> service.submit(101,
            new SupportTicketService.Submission("PLATFORM_QUESTION","Different subject",request.message(),requestId)));
        assertEquals(HttpStatus.CONFLICT,conflict.getStatusCode());
        verify(db,times(3)).update(contains("ON CONFLICT (request_key) DO NOTHING"),eq(101),anyString(),anyString(),anyString(),eq(requestId));
    }

    @Test
    void refusesResolutionWithoutResponseAndReportsConcurrentOrResolvedTickets() {
        var invalid = assertThrows(ResponseStatusException.class, () -> service.update(81,new SupportTicketService.Transition("RESOLVED","  ",0)));
        assertEquals(HttpStatus.BAD_REQUEST, invalid.getStatusCode());
        verifyNoInteractions(db);
        var conflict = assertThrows(ResponseStatusException.class, () -> service.update(81,new SupportTicketService.Transition("IN_PROGRESS","Reviewing",0)));
        assertEquals(HttpStatus.CONFLICT, conflict.getStatusCode());
    }

    @Test
    void rejectsInvalidTopicBeforeAnyDatabaseWrite() {
        var error = assertThrows(ResponseStatusException.class, () -> service.submit(101,new SupportTicketService.Submission("TRADE_DISPUTE","Theme","Question",requestId)));
        assertEquals(HttpStatus.BAD_REQUEST,error.getStatusCode()); verifyNoInteractions(db);
    }
}
