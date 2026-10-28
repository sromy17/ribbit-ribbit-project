package com.neueda.leap.trading.security;

import java.nio.charset.StandardCharsets;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpMethod;
import org.springframework.security.authorization.AuthorizationDecision;
import org.springframework.security.core.Authentication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
@EnableWebSecurity
public class SecurityConfig {
    private final SessionTokenValidationFilter sessionTokenValidationFilter;

    public SecurityConfig(SessionTokenValidationFilter sessionTokenValidationFilter) {
        this.sessionTokenValidationFilter = sessionTokenValidationFilter;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http)
        throws Exception {
        JwtGrantedAuthoritiesConverter authoritiesConverter = new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter authenticationConverter = new JwtAuthenticationConverter();
        authenticationConverter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);

        http
            .csrf(csrf -> csrf.disable())
            .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(authorize -> authorize
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                .requestMatchers("/admin/tickets/**").access((authentication, context) -> {
                    var auth = authentication.get();
                    return new AuthorizationDecision(isEmployee(auth) && auth.getAuthorities().stream()
                        .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority())));
                })
                .requestMatchers(HttpMethod.GET, "/accounts/user/*", "/accounts/*/orders",
                    "/accounts/*/trades", "/accounts/*/holdings", "/accounts/*/wallet/activity")
                    .access((authentication, context) -> {
                        var auth = authentication.get();
                        boolean allowed = !isEmployee(auth) || auth.getAuthorities().stream()
                            .anyMatch(authority -> "ROLE_ADMIN".equals(authority.getAuthority()));
                        return new AuthorizationDecision(auth.isAuthenticated() && allowed);
                    })
                .anyRequest().access((authentication, context) -> {
                    var auth = authentication.get();
                    return new AuthorizationDecision(auth.isAuthenticated() && !isEmployee(auth));
                }))
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt.jwtAuthenticationConverter(authenticationConverter)))
            .addFilterAfter(sessionTokenValidationFilter, BearerTokenAuthenticationFilter.class);

        return http.build();
    }

    private static boolean isEmployee(Authentication authentication) {
        return authentication instanceof JwtAuthenticationToken token
            && "employee".equals(token.getToken().getClaimAsString("principal_type"));
    }

    @Bean
    public JwtDecoder jwtDecoder(@Value("${security.jwt.shared-secret}") String sharedSecret) {
        SecretKey key = new SecretKeySpec(sharedSecret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
        return NimbusJwtDecoder.withSecretKey(key)
            .macAlgorithm(MacAlgorithm.HS256)
            .build();
    }

    @Bean
    public PasswordEncoder passwordEncoder() {
        return new BCryptPasswordEncoder();
    }
}
