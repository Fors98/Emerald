package com.emerald.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.security.oauth2.server.resource.web.DefaultBearerTokenResolver;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

/**
 * Security filter chain: stateless, Bearer-JWT protected, deny by default.
 *
 * Public endpoints are listed explicitly and mirror api.yaml. Anything not
 * listed is denied, so a new controller is private until someone consciously
 * opens it here. /admin/** requires the ADMIN role.
 */
@Configuration
@EnableWebSecurity
@EnableConfigurationProperties(AuthProperties.class)
public class SecurityConfig {

    /**
     * Argon2id (OWASP-recommended): 32 MiB memory, 3 iterations, 1 lane, 16-byte salt, 32-byte hash.
     * Costs are encoded in each hash, so they can be raised later without breaking existing hashes.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        return new Argon2PasswordEncoder(16, 32, 1, 32 * 1024, 3);
    }

    @Bean
    public JwtDecoder jwtDecoder(JwtService jwtService, RefreshTokenRepository refreshTokens) {
        return jwtService.decoder(new ActiveSessionValidator(refreshTokens));
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http,
                                                   JwtDecoder jwtDecoder,
                                                   CorsConfigurationSource corsConfigurationSource,
                                                   AuthProperties properties) throws Exception {

        AuthenticationEntryPoint unauthorized = (request, response, exception) -> {
            response.setStatus(401);
            response.setHeader("WWW-Authenticate", "Bearer");
            writeJson(response, "{\"message\":\"Authentication required.\"}");
        };
        AccessDeniedHandler forbidden = (request, response, exception) -> {
            response.setStatus(403);
            writeJson(response, "{\"message\":\"Access denied.\"}");
        };

        http
                // Stateless Bearer API: no session, no ambient credentials, so classic CSRF does not apply.
                // The only cookie (refresh) is SameSite=Strict, path-scoped and Origin-checked in AuthController.
                .csrf(AbstractHttpConfigurer::disable)
                .cors(Customizer.withDefaults())
                .sessionManagement(s -> s.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .requestCache(cache -> cache.disable())
                .headers(headers -> headers
                        .contentTypeOptions(Customizer.withDefaults())
                        .frameOptions(frame -> frame.deny())
                        .referrerPolicy(ref -> ref.policy(ReferrerPolicyHeaderWriter.ReferrerPolicy.NO_REFERRER))
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        // JSON API: nothing in a response should ever be rendered or embedded.
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'")))
                .authorizeHttpRequests(auth -> auth
                        // Authentication endpoints (logout/refresh authenticate via the refresh cookie)
                        .requestMatchers(HttpMethod.POST, "/auth/login", "/auth/refresh", "/auth/logout").permitAll()
                        .requestMatchers(HttpMethod.GET, "/auth/me").authenticated()
                        // Public API (api.yaml). Keep in sync when public controllers are added.
                        .requestMatchers(HttpMethod.POST, "/contact",
                                "/newsletter/subscribe", "/newsletter/unsubscribe").permitAll()
                        .requestMatchers(HttpMethod.GET, "/services", "/projects", "/projects/*",
                                "/testimonials", "/faqs", "/stats", "/media/*").permitAll()
                        // Framework error dispatch (exposes no details: see server.error.* settings)
                        .requestMatchers("/error").permitAll()
                        // Admin area: least privilege, explicit role
                        .requestMatchers("/admin/**").hasRole("ADMIN")
                        // Everything else is closed
                        .anyRequest().denyAll())
                .oauth2ResourceServer(oauth -> oauth
                        .bearerTokenResolver(bearerTokenResolver())
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden)
                        .jwt(jwt -> jwt
                                .decoder(jwtDecoder)
                                .jwtAuthenticationConverter(jwtAuthenticationConverter())))
                .exceptionHandling(ex -> ex
                        .authenticationEntryPoint(unauthorized)
                        .accessDeniedHandler(forbidden));

        if (properties.requireHttps()) {
            http.requiresChannel(channel -> channel.anyRequest().requiresSecure());
        }
        return http.build();
    }

    /**
     * Only look at the Authorization header on endpoints that need it. Public
     * endpoints (login, refresh, logout, contact...) then ignore a stale or
     * garbage Bearer header instead of failing with 401, which is what lets
     * logout/refresh work after the access token has expired. Fail-safe: if a
     * protected path were ever missed here, the request would be anonymous and
     * therefore denied.
     */
    @Bean
    public BearerTokenResolver bearerTokenResolver() {
        DefaultBearerTokenResolver delegate = new DefaultBearerTokenResolver();
        return (HttpServletRequest request) -> {
            String path = request.getRequestURI().substring(request.getContextPath().length());
            boolean protectedPath = path.equals("/auth/me") || path.equals("/admin") || path.startsWith("/admin/");
            return protectedPath ? delegate.resolve(request) : null;
        };
    }

    /** Maps the "roles" claim to ROLE_* authorities (roles: ["ADMIN"] -> ROLE_ADMIN). */
    private JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authorities = new JwtGrantedAuthoritiesConverter();
        authorities.setAuthoritiesClaimName(JwtService.CLAIM_ROLES);
        authorities.setAuthorityPrefix("ROLE_");
        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authorities);
        return converter;
    }

    /**
     * CORS is closed unless origins are listed in emerald.auth.allowed-origins.
     * With the site and API on the same origin (api.yaml: server url "/api")
     * browsers never need CORS at all.
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource(AuthProperties properties) {
        CorsConfiguration config = new CorsConfiguration();
        config.setAllowedOrigins(properties.allowedOrigins());
        config.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE"));
        config.setAllowedHeaders(List.of("Authorization", "Content-Type", "Accept"));
        config.setAllowCredentials(true); // needed for the refresh cookie on cross-origin (same-site) setups
        config.setMaxAge(Duration.ofHours(1));

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", config);
        return source;
    }

    private static void writeJson(HttpServletResponse response, String json) throws IOException {
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.getWriter().write(json);
    }
}
