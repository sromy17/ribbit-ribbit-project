package com.neueda.leap.trading.api;

import com.neueda.leap.trading.service.impl.SupportTicketService;
import com.neueda.leap.trading.repository.jpa.AccessTokenRepository;
import java.util.List;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.http.MediaType;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(SupportTicketController.class)
@AutoConfigureMockMvc(addFilters=false)
@Import(SupportTicketControllerTest.MethodSecurity.class)
class SupportTicketControllerTest {
    @TestConfiguration @EnableMethodSecurity static class MethodSecurity {}
    @Autowired MockMvc mvc;
    @Autowired SupportTicketController controller;
    @MockitoBean SupportTicketService service;
    @MockitoBean AccessTokenRepository tokenRepository;
    @AfterEach void clear() { SecurityContextHolder.clearContext(); }

    @Test
    void rejectsEmptyAndOversizedQuestionsBeforeSubmission() throws Exception {
        mvc.perform(post("/accounts/101/support-tickets").contentType(MediaType.APPLICATION_JSON)
            .content("{\"category\":\"PLATFORM_QUESTION\",\"subject\":\" \",\"message\":\"Question\",\"requestId\":\"5fbaea9f-7b68-41d9-bb5d-510385fd4a0b\"}"))
            .andExpect(status().isBadRequest());
        String longMessage="x".repeat(2001);
        mvc.perform(post("/accounts/101/support-tickets").contentType(MediaType.APPLICATION_JSON)
            .content("{\"category\":\"PLATFORM_QUESTION\",\"subject\":\"Help\",\"message\":\""+longMessage+"\",\"requestId\":\"5fbaea9f-7b68-41d9-bb5d-510385fd4a0b\"}"))
            .andExpect(status().isBadRequest());
        verifyNoInteractions(service);
    }

    @Test
    void onlyAuthorizedStaffCanReadQueueOrUpdateResponses() {
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("client","unused","ROLE_USER"));
        assertThrows(AccessDeniedException.class, () -> controller.staff("OPEN",0));
        assertThrows(AccessDeniedException.class, () -> controller.update(81,new SupportTicketService.Transition("RESOLVED","Done",0)));
        SecurityContextHolder.getContext().setAuthentication(new TestingAuthenticationToken("staff","unused","PERM_DISPUTE_READ"));
        when(service.staffTickets("OPEN",0)).thenReturn(new SupportTicketService.Page(List.of(),0,0,20));
        assertEquals(0,controller.staff("OPEN",0).total());
        assertThrows(AccessDeniedException.class, () -> controller.update(81,new SupportTicketService.Transition("RESOLVED","Done",0)));
    }
}
