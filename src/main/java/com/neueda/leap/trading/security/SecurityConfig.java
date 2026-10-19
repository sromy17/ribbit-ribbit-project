package com.neueda.leap.trading.security;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Locale;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import com.nimbusds.jose.jwk.source.ImmutableSecret;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
    private final SessionTokenValidationFilter sessionTokenValidationFilter;

    public SecurityConfig(SessionTokenValidationFilter sessionTokenValidationFilter) {
        this.sessionTokenValidationFilter = sessionTokenValidationFilter;
    }

    @Bean
    SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())
            .authorizeHttpRequests(auth -> auth
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/login", "/logout").permitAll()
                .requestMatchers("/users").permitAll()
                .requestMatchers("/admin/**").hasAuthority("PERM_STAFF_ADMIN")
                .requestMatchers("/etl/**").hasAuthority("PERM_ETL_TRIGGER")
                .requestMatchers("/exports/**").hasAuthority("PERM_EXPORT")
                .requestMatchers("/reports/**").hasAuthority("PERM_REPORTING")
                .anyRequest().authenticated())
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())))
            .addFilterAfter(sessionTokenValidationFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    JwtDecoder jwtDecoder(@Value("${security.jwt.shared-secret}") String sharedSecret) {
        SecretKey key = new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
    }

    @Bean
    PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }

    @Bean
    JwtEncoder jwtEncoder(
        @Value("${security.jwt.shared-secret}") String sharedSecret
    ) {
        SecretKey key = new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(this::authoritiesFrom);
        return converter;
    }

    private Collection<GrantedAuthority> authoritiesFrom(Jwt jwt) {
        Collection<GrantedAuthority> authorities = new ArrayList<>();
        Object rolesClaim = jwt.getClaims().get("roles");
        if (!(rolesClaim instanceof Collection<?> roles)) {
            return authorities;
        }

        for (Object roleValue : roles) {
            if (roleValue == null) {
                continue;
            }
            String role = roleValue.toString().trim().toUpperCase(Locale.ROOT);
            if (role.isEmpty()) {
                continue;
            }
            authorities.add(new SimpleGrantedAuthority("ROLE_" + role));
            permissionsFor(role).forEach(permission ->
                authorities.add(new SimpleGrantedAuthority(permission)));
        }
        return authorities;
    }

    private Collection<String> permissionsFor(String role) {
        return switch (role) {
            case "OPERATIONS" -> java.util.List.of(
                "PERM_ORDER_INVESTIGATION", "PERM_ACCOUNT_DETAILS", "PERM_HISTORICAL_EVIDENCE");
            case "COMPLIANCE" -> java.util.List.of(
                "PERM_ORDER_INVESTIGATION", "PERM_ACCOUNT_DETAILS", "PERM_HISTORICAL_EVIDENCE", "PERM_AUDIT_READ");
            case "ANALYST" -> java.util.List.of(
                "PERM_REPORTING", "PERM_LIMITED_CLIENT_IDENTIFIERS");
            case "PLATFORM_ADMIN", "PLATFORM_ADMINISTRATOR" -> java.util.List.of(
                "PERM_STAFF_ADMIN", "PERM_ETL_TRIGGER", "PERM_EXPORT", "PERM_AUDIT_READ");
            case "USER", "CLIENT" -> java.util.List.of("PERM_ACCOUNT_SELF", "PERM_ORDER_SELF");
            default -> java.util.List.of();
        };
    }
}
