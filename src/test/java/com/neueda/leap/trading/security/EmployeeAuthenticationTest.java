package com.neueda.leap.trading.security;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import com.neueda.leap.trading.domain.AccessToken;
import com.neueda.leap.trading.repository.jpa.AccessTokenRepository;

@WebMvcTest(EmployeeAuthenticationTest.TestController.class)
@Import({SecurityConfig.class, SessionTokenValidationFilter.class, EmployeeAuthenticationTest.TestController.class})
class EmployeeAuthenticationTest {
    @Autowired private MockMvc mvc;
    @MockBean private JwtDecoder decoder;
    @MockBean(name = "oltpJdbcTemplate") private JdbcTemplate jdbc;
    @MockBean(name = "olapJdbcTemplate") private JdbcTemplate olapJdbc;
    @MockBean private AccessTokenRepository accessTokens;

    @RestController
    static class TestController {
        @GetMapping({"/accounts/user/1", "/accounts/101/orders", "/accounts/101/trades",
            "/accounts/101/holdings", "/accounts/101/wallet/activity", "/users"})
        String read() { return "ok"; }
        @PostMapping("/accounts/101/orders")
        String trade() { return "ok"; }
    }

    @BeforeEach
    void setUp() {
        when(decoder.decode("admin-token")).thenReturn(employeeJwt("admin-token", "ADMIN"));
        when(decoder.decode("analyst-token")).thenReturn(employeeJwt("analyst-token", "ANALYST"));
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
            anyString(), anyLong(), anyString(), anyString())).thenReturn(1);
    }

    @AfterEach
    void neverReadsEmployeeSessionsFromAnalytics() {
        verifyNoInteractions(olapJdbc);
    }

    @Test
    void adminCanReadAllDashboardEndpoints() throws Exception {
        for (String path : List.of("/accounts/user/1", "/accounts/101/orders", "/accounts/101/trades",
            "/accounts/101/holdings", "/accounts/101/wallet/activity")) {
            mvc.perform(get(path).header("Authorization", "Bearer admin-token")).andExpect(status().isOk());
        }
        verifyNoInteractions(accessTokens);
    }

    @Test
    void employeeCannotUseInvestorMutationsOrUnrelatedEndpoints() throws Exception {
        mvc.perform(post("/accounts/101/orders").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/users").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isForbidden());
        mvc.perform(get("/accounts/user/1").header("Authorization", "Bearer analyst-token"))
            .andExpect(status().isForbidden());
    }

    @Test
    void missingRevokedExpiredOrRoleChangedEmployeeSessionIsRejected() throws Exception {
        when(jdbc.queryForObject(anyString(), eq(Integer.class),
            anyString(), anyLong(), anyString(), anyString())).thenReturn(0);
        mvc.perform(get("/accounts/user/1").header("Authorization", "Bearer admin-token"))
            .andExpect(status().isUnauthorized());
        verify(jdbc).queryForObject(contains("e.employeeroles = ?"), eq(Integer.class),
            eq(TokenHash.sha256("admin-token")), eq(1L), eq("employee"), eq("ADMIN"));
    }

    @Test
    void investorSessionStillUsesOriginalTokenRepositoryAndPermissions() throws Exception {
        Jwt investor = Jwt.withTokenValue("investor-token").header("alg", "HS256")
            .subject("investor").claim("uid", 1).claim("roles", List.of("MISSION_OPERATOR")).build();
        when(decoder.decode("investor-token")).thenReturn(investor);
        AccessToken token = new AccessToken();
        ReflectionTestUtils.setField(token, "dateIssued", Instant.now().minusSeconds(10));
        ReflectionTestUtils.setField(token, "dateExpires", Instant.now().plusSeconds(1200));
        when(accessTokens.findByAccessTokenHashAndRevokedFalseAndDateExpiresAfter(anyString(), any()))
            .thenReturn(Optional.of(token));
        mvc.perform(post("/accounts/101/orders").header("Authorization", "Bearer investor-token"))
            .andExpect(status().isOk());
        verifyNoInteractions(jdbc);
    }

    private Jwt employeeJwt(String token, String role) {
        return Jwt.withTokenValue(token).header("alg", "HS256").subject("employee")
            .claim("principal_type", "employee").claim("eid", 1L).claim("roles", List.of(role)).build();
    }
}
