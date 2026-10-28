package com.neueda.leap.trading.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;
import java.util.Optional;
import java.time.Instant;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import com.neueda.leap.trading.domain.AccessToken;
import com.neueda.leap.trading.repository.jpa.AccessTokenRepository;
import com.neueda.leap.trading.security.SecurityConfig;
import com.neueda.leap.trading.security.SessionTokenValidationFilter;
import com.neueda.leap.trading.service.impl.TicketService;

@WebMvcTest(TicketController.class)
@Import({SecurityConfig.class, SessionTokenValidationFilter.class})
class TicketControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private TicketService tickets;
    @MockBean private JwtDecoder decoder;
    @MockBean private AccessTokenRepository accessTokens;
    @MockBean(name = "oltpJdbcTemplate") private JdbcTemplate jdbc;
    @MockBean(name = "olapJdbcTemplate") private JdbcTemplate olap;

    @BeforeEach
    void setUp() {
        when(decoder.decode("investor")).thenReturn(jwt("investor", false, "MISSION_OPERATOR"));
        when(decoder.decode("admin")).thenReturn(jwt("admin", true, "ADMIN"));
        when(decoder.decode("analyst")).thenReturn(jwt("analyst", true, "ANALYST"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class), anyString(), anyLong(), anyString(), anyString())).thenReturn(1);
        AccessToken token = new AccessToken();
        ReflectionTestUtils.setField(token, "dateIssued", Instant.now().minusSeconds(10));
        ReflectionTestUtils.setField(token, "dateExpires", Instant.now().plusSeconds(1200));
        when(accessTokens.findByAccessTokenHashAndRevokedFalseAndDateExpiresAfter(anyString(), any()))
            .thenReturn(Optional.of(token));
    }

    @Test
    void investorCreatesDisputeAndSupportWithoutSupplyingAnEmployeeIdentity() throws Exception {
        when(tickets.createDispute(any(), eq(101), eq(2001), eq("Fraud"), eq("Please investigate"))).thenReturn(9);
        mvc.perform(post("/accounts/101/orders/2001/disputes").header("Authorization", "Bearer investor")
            .contentType("application/json").content("{\"category\":\"Fraud\",\"reason\":\"Please investigate\"}"))
            .andExpect(status().isCreated()).andExpect(jsonPath("$.ticketId").value(9));
        mvc.perform(post("/accounts/101/support-tickets").header("Authorization", "Bearer investor")
            .contentType("application/json").content("{\"reason\":\"Account question\"}"))
            .andExpect(status().isCreated());
    }

    @Test
    void validatesReasonsAndCategories() throws Exception {
        for (String body : List.of("{\"category\":\"Invalid\",\"reason\":\"test\"}",
            "{\"category\":\"Fraud\",\"reason\":\"   \"}",
            "{\"category\":\"Fraud\",\"reason\":\"" + "x".repeat(76) + "\"}")) {
            mvc.perform(post("/accounts/101/orders/2001/disputes").header("Authorization", "Bearer investor")
                .contentType("application/json").content(body)).andExpect(status().isBadRequest());
        }
        verifyNoInteractions(tickets);
    }

    @Test
    void adminCanReviewAndResolveButInvestorsAndAnalystsCannot() throws Exception {
        mvc.perform(get("/admin/tickets/dispute").header("Authorization", "Bearer admin")).andExpect(status().isOk());
        mvc.perform(patch("/admin/tickets/support/9").header("Authorization", "Bearer admin")
            .contentType("application/json").content("{\"status\":\"RESOLVED\",\"expectedStatus\":\"OPEN\"}"))
            .andExpect(status().isNoContent());
        for (String identity : List.of("investor", "analyst")) {
            mvc.perform(get("/admin/tickets/dispute").header("Authorization", "Bearer " + identity))
                .andExpect(status().isForbidden());
            mvc.perform(patch("/admin/tickets/support/9").header("Authorization", "Bearer " + identity)
                .contentType("application/json").content("{\"status\":\"RESOLVED\",\"expectedStatus\":\"OPEN\"}"))
                .andExpect(status().isForbidden());
        }
        mvc.perform(post("/accounts/101/support-tickets").header("Authorization", "Bearer admin")
            .contentType("application/json").content("{\"reason\":\"Staff cannot submit\"}"))
            .andExpect(status().isForbidden());
    }

    @Test
    void requiresOwnershipBeforeListingAndSurfacesDuplicateErrors() throws Exception {
        mvc.perform(get("/accounts/101/tickets/support").header("Authorization", "Bearer investor"))
            .andExpect(status().isOk());
        verify(tickets).requireOwner(any(), eq(101));
        when(tickets.createDispute(any(), anyInt(), anyInt(), anyString(), anyString()))
            .thenThrow(new ResponseStatusException(HttpStatus.CONFLICT, "This order already has an open dispute"));
        mvc.perform(post("/accounts/101/orders/2001/disputes").header("Authorization", "Bearer investor")
            .contentType("application/json").content("{\"category\":\"Fraud\",\"reason\":\"Duplicate\"}"))
            .andExpect(status().isConflict()).andExpect(jsonPath("$.message").value("This order already has an open dispute"));
    }

    private Jwt jwt(String token, boolean employee, String role) {
        var builder = Jwt.withTokenValue(token).header("alg", "HS256").subject(token).claim("roles", List.of(role));
        if (employee) builder.claim("principal_type", "employee").claim("eid", 1);
        else builder.claim("uid", 1);
        return builder.build();
    }
}
