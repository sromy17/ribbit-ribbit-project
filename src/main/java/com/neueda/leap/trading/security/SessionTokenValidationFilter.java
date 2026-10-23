package com.neueda.leap.trading.security;

import java.io.IOException;
import java.time.Instant;

import org.springframework.http.MediaType;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;
import org.jspecify.annotations.NonNull;
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

    public SessionTokenValidationFilter(AccessTokenRepository accessTokenRepository) {
        this.accessTokenRepository = accessTokenRepository;
    }

    @Override
    protected boolean shouldNotFilter(@NonNull HttpServletRequest request) {
        String path = request.getServletPath();
        return "/actuator/health".equals(path) || "/actuator/info".equals(path);
    }

    @Override
    protected void doFilterInternal(
        @NonNull HttpServletRequest request,
        @NonNull HttpServletResponse response,
        @NonNull FilterChain filterChain
    )
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

        Object uid=jwt.getClaim("uid");
        if(!(uid instanceof Number)) { reject(response,"Session is invalid"); return; }
        AccessToken accessToken = accessTokenRepository
            .findValidToken(TokenHash.sha256(rawToken),((Number)uid).intValue(),Instant.now())
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

    private void reject(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.getWriter().write("{\"message\":\"" + message + "\"}");
    }

}
