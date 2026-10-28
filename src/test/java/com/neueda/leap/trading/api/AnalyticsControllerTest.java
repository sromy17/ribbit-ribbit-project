package com.neueda.leap.trading.api;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;

import com.neueda.leap.etl.OlapEtlService;
import com.neueda.leap.trading.domain.AccessToken;
import com.neueda.leap.trading.repository.jpa.AccessTokenRepository;
import com.neueda.leap.trading.security.SecurityConfig;
import com.neueda.leap.trading.security.SessionTokenValidationFilter;
import com.neueda.leap.trading.service.impl.AnalyticsService;
import com.neueda.leap.trading.service.impl.AnalyticsInsightsService.Period;
import com.neueda.leap.trading.service.impl.AnalyticsInsightsService;

@WebMvcTest({AnalyticsController.class, EtlController.class})
@Import({SecurityConfig.class, SessionTokenValidationFilter.class})
class AnalyticsControllerTest {
    @Autowired private MockMvc mvc;
    @MockBean private AnalyticsService analytics;
    @MockBean private OlapEtlService etl;
    @MockBean private JwtDecoder decoder;
    @MockBean private AccessTokenRepository tokens;
    @MockBean(name = "oltpJdbcTemplate") private JdbcTemplate oltp;
    @MockBean(name = "olapJdbcTemplate") private JdbcTemplate olap;

    @BeforeEach
    void setup() {
        for (String role : List.of("ANALYST", "ADMIN", "MISSION_OPERATOR")) {
            var builder = Jwt.withTokenValue(role).header("alg", "HS256").subject(role)
                .claim("roles", List.of(role));
            if (!role.equals("MISSION_OPERATOR")) builder.claim("principal_type", "employee").claim("eid", 1);
            when(decoder.decode(role)).thenReturn(builder.build());
        }
        when(decoder.decode("forged")).thenReturn(Jwt.withTokenValue("forged").header("alg", "HS256")
            .subject("investor").claim("roles", List.of("ANALYST")).build());
        when(oltp.queryForObject(anyString(), eq(Integer.class), anyString(), anyLong(), anyString(), anyString()))
            .thenReturn(1);
        var token = new AccessToken();
        ReflectionTestUtils.setField(token, "dateIssued", Instant.now().minusSeconds(1));
        ReflectionTestUtils.setField(token, "dateExpires", Instant.now().plusSeconds(1200));
        when(tokens.findByAccessTokenHashAndRevokedFalseAndDateExpiresAfter(anyString(), any()))
            .thenReturn(Optional.of(token));
    }

    @Test
    void onlyAnalystsCanReadReportsAndTriggerEtl() throws Exception {
        when(etl.runNow()).thenReturn(new OlapEtlService.EtlResult(true, "Completed"));
        mvc.perform(get("/analyst/overview").header("Authorization", "Bearer ANALYST")).andExpect(status().isOk());
        mvc.perform(post("/etl/run").header("Authorization", "Bearer ANALYST"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.success").value(true));
        for (String role : List.of("ADMIN", "MISSION_OPERATOR")) {
            mvc.perform(get("/analyst/overview").header("Authorization", "Bearer " + role)).andExpect(status().isForbidden());
            mvc.perform(post("/etl/run").header("Authorization", "Bearer " + role)).andExpect(status().isForbidden());
        }
        mvc.perform(get("/analyst/overview")).andExpect(status().isUnauthorized());
        mvc.perform(post("/etl/run")).andExpect(status().isUnauthorized());
        mvc.perform(get("/analyst/overview").header("Authorization", "Bearer forged"))
            .andExpect(status().isForbidden());
        mvc.perform(post("/etl/run").header("Authorization", "Bearer forged"))
            .andExpect(status().isForbidden());
        verify(etl, times(1)).runNow();
    }

    @Test
    void reportingAndRefreshFailuresAreExplicit() throws Exception {
        when(analytics.report()).thenThrow(new DataAccessResourceFailureException("database unavailable"));
        mvc.perform(get("/analyst/overview").header("Authorization", "Bearer ANALYST"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.message").exists());
        when(etl.runNow()).thenReturn(new OlapEtlService.EtlResult(false, "Refresh failed"));
        mvc.perform(post("/etl/run").header("Authorization", "Bearer ANALYST"))
            .andExpect(status().isServiceUnavailable()).andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void acceptsSupportedPeriodsAndRejectsUnknownOnes() throws Exception {
        var insights = new AnalyticsInsightsService.Insights(Period.LAST_30_DAYS, "2026-09-29", "2026-10-28",
            new AnalyticsInsightsService.Funding(java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(200),
                java.math.BigDecimal.valueOf(-100), 1, 1, java.math.BigDecimal.valueOf(100), java.math.BigDecimal.valueOf(100)),
            List.of(new AnalyticsInsightsService.Bucket("$100 to under $1,000", 1)), List.of(), List.of(),
            new AnalyticsInsightsService.Execution(0, 0, null, List.of()),
            new AnalyticsInsightsService.Participation(0, 0, 0, null), List.of());
        when(analytics.report(Period.LAST_30_DAYS)).thenReturn(new AnalyticsService.Report(
            Instant.now(), null, null, List.of(), List.of(), insights));
        mvc.perform(get("/analyst/overview").param("period", "LAST_30_DAYS")
            .header("Authorization", "Bearer ANALYST")).andExpect(status().isOk())
            .andExpect(jsonPath("$.insights.period").value("LAST_30_DAYS"))
            .andExpect(jsonPath("$.insights.funding.netDeposits").value(-100))
            .andExpect(jsonPath("$.insights.depositDistribution[0].deposits").value(1))
            .andExpect(jsonPath("$.insights.execution.shareFillRate").isEmpty())
            .andExpect(jsonPath("$.insights.topStocks").isArray());
        verify(analytics).report(Period.LAST_30_DAYS);
        mvc.perform(get("/analyst/overview").param("period", "LAST_90_DAYS")
            .header("Authorization", "Bearer ANALYST")).andExpect(status().isOk());
        verify(analytics).report(Period.LAST_90_DAYS);
        mvc.perform(get("/analyst/overview").param("period", "UNKNOWN")
            .header("Authorization", "Bearer ANALYST")).andExpect(status().isBadRequest());
    }
}
