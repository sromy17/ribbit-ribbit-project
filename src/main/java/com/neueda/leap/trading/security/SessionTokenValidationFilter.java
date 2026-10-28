package com.neueda.leap.trading.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.lang.NonNull;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.neueda.leap.trading.domain.AccessToken;
import com.neueda.leap.trading.repository.jpa.AccessTokenRepository;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

@Component
public class SessionTokenValidationFilter extends OncePerRequestFilter {
    private final AccessTokenRepository accessTokenRepository;
    private final JdbcTemplate jdbcTemplate;

    public SessionTokenValidationFilter(AccessTokenRepository accessTokenRepository,
        @Qualifier("oltpJdbcTemplate") JdbcTemplate jdbcTemplate) {
        this.accessTokenRepository = accessTokenRepository;
        this.jdbcTemplate = jdbcTemplate;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return "/actuator/health".equals(path) || "/actuator/info".equals(path);
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain)
        throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        if (!(authentication instanceof JwtAuthenticationToken jwtAuthenticationToken)) {
            filterChain.doFilter(request, response);
            return;
        }

        Jwt jwt = jwtAuthenticationToken.getToken();
        String rawToken = jwt.getTokenValue();
        if (rawToken == null || rawToken.isBlank()) {
            reject(response, "Session is invalid");
            return;
        }

        if ("employee".equals(jwt.getClaimAsString("principal_type"))) {
            if (!validEmployeeSession(jwt, rawToken)) {
                reject(response, "Employee session is invalid");
                return;
            }
            filterChain.doFilter(request, response);
            return;
        }

        AccessToken accessToken = accessTokenRepository
            .findByAccessTokenHashAndRevokedFalseAndDateExpiresAfter(
                TokenHash.sha256(rawToken),
                Instant.now()
            )
            .orElse(null);
        if (accessToken == null) {
            reject(response, "Session is invalid");
            return;
        }

        if (accessToken.getDateIssued() == null || accessToken.getDateExpires() == null) {
            reject(response, "Session is invalid");
            return;
        }

        filterChain.doFilter(request, response);
    }

    private boolean validEmployeeSession(Jwt jwt, String rawToken) {
        Object employeeId = jwt.getClaim("eid");
        var roles = jwt.getClaimAsStringList("roles");
        if (!(employeeId instanceof Number id) || roles == null || roles.size() != 1
            || !("ADMIN".equals(roles.get(0)) || "ANALYST".equals(roles.get(0)))) {
            return false;
        }
        Integer matches = jdbcTemplate.queryForObject("""
            SELECT COUNT(*)
            FROM access_tokens_employee at
            JOIN refresh_tokens_employee rt ON rt.refreshtokenid = at.refreshtokenid
            JOIN employees e ON e.employeeid = rt.employeeid
            WHERE at.accesstokenhash = ? AND at.isrevoked = FALSE
              AND at.dateexpires > CURRENT_TIMESTAMP
              AND rt.isrevoked = FALSE AND rt.dateexpires > CURRENT_TIMESTAMP
              AND e.employeeid = ? AND e.employeeusername = ? AND e.employeeroles = ?
            """, Integer.class, TokenHash.sha256(rawToken), id.longValue(), jwt.getSubject(), roles.get(0));
        return matches != null && matches == 1;
    }

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }
}
