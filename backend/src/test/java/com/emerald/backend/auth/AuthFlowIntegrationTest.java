package com.emerald.backend.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.nimbusds.jose.jwk.source.ImmutableSecret;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * End-to-end checks of the authentication flow against the real filter chain
 * (in-memory H2). Every test uses its own client IP so the in-memory IP limiter
 * never leaks between tests.
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthFlowIntegrationTest {

    private static final String ADMIN_EMAIL = "admin@emerald.test";
    private static final String ADMIN_PASSWORD = "correct-horse-battery-staple";
    private static final String ORIGIN = "https://emerald.test";
    private static final String COOKIE = "emerald_rt";

    @Autowired MockMvc mvc;
    @Autowired ObjectMapper json;
    @Autowired AdminUserRepository users;
    @Autowired RefreshTokenRepository tokens;
    @Autowired PasswordEncoder encoder;
    @Autowired JwtDecoder jwtDecoder;
    @Value("${emerald.auth.jwt.secret}") String secret;

    // ------------------------------------------------------------------ login

    @Test
    void loginSucceedsAndSetsHardenedRefreshCookie() throws Exception {
        MvcResult result = login(ADMIN_EMAIL, ADMIN_PASSWORD, "10.0.0.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(600))
                .andExpect(jsonPath("$.user.email").value(ADMIN_EMAIL))
                .andExpect(jsonPath("$.user.role").value("ADMIN"))
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andExpect(header().string(HttpHeaders.CACHE_CONTROL, org.hamcrest.Matchers.containsString("no-store")))
                .andReturn();

        String setCookie = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(setCookie).startsWith(COOKIE + "=")
                .contains("HttpOnly").contains("Secure").contains("SameSite=Strict").contains("Path=/api/auth");

        String raw = cookieValue(result);
        assertThat(result.getResponse().getContentAsString()).doesNotContain(raw);
        // the database stores only a hash, never the raw token
        assertThat(tokens.findByTokenHash(raw)).isEmpty();
    }

    @Test
    void wrongPasswordAndUnknownUserAreIndistinguishable() throws Exception {
        MvcResult wrongPassword = login(ADMIN_EMAIL, "not-the-password", "10.0.0.2")
                .andExpect(status().isUnauthorized()).andReturn();
        MvcResult unknownUser = login("nobody@emerald.test", "not-the-password", "10.0.0.2")
                .andExpect(status().isUnauthorized()).andReturn();

        assertThat(wrongPassword.getResponse().getContentAsString())
                .isEqualTo(unknownUser.getResponse().getContentAsString());
        assertThat(wrongPassword.getResponse().getHeader(HttpHeaders.SET_COOKIE)).isNull();
    }

    @Test
    void loginValidatesInput() throws Exception {
        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\":\"not-an-email\",\"password\":\"\"}")
                        .with(ip("10.0.0.3")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.email").exists())
                .andExpect(jsonPath("$.errors.password").exists());

        mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                        .content("{not json").with(ip("10.0.0.3")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Malformed request body."));
    }

    @Test
    void accountIsLockedAfterRepeatedFailuresEvenForTheCorrectPassword() throws Exception {
        users.save(new AdminUser("lockme@emerald.test", encoder.encode("a-perfectly-fine-password"), AdminUser.ROLE_ADMIN));

        for (int i = 0; i < 5; i++) {
            login("lockme@emerald.test", "wrong-" + i, "10.0.0.4").andExpect(status().isUnauthorized());
        }
        // Locked: same generic 401 (no 423/429 that would reveal the account exists)
        login("lockme@emerald.test", "a-perfectly-fine-password", "10.0.0.4")
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Incorrect email or password."));
    }

    @Test
    void clientAddressIsThrottledAfterTooManyFailures() throws Exception {
        for (int i = 0; i < 10; i++) {
            login("ghost" + i + "@emerald.test", "whatever", "10.0.0.5").andExpect(status().isUnauthorized());
        }
        login(ADMIN_EMAIL, ADMIN_PASSWORD, "10.0.0.5")
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists(HttpHeaders.RETRY_AFTER));
    }

    // ---------------------------------------------------------------- refresh

    @Test
    void refreshRotatesTheTokenAndIssuesANewAccessToken() throws Exception {
        MvcResult first = login(ADMIN_EMAIL, ADMIN_PASSWORD, "10.0.1.1").andReturn();
        String oldCookie = cookieValue(first);

        MvcResult second = refresh(oldCookie, ORIGIN, "10.0.1.1")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").doesNotExist())
                .andReturn();

        String newCookie = cookieValue(second);
        assertThat(newCookie).isNotEqualTo(oldCookie);
        me(accessToken(second)).andExpect(status().isOk()).andExpect(jsonPath("$.email").value(ADMIN_EMAIL));
    }

    @Test
    void replayingARotatedRefreshTokenRevokesTheWholeSession() throws Exception {
        MvcResult first = login(ADMIN_EMAIL, ADMIN_PASSWORD, "10.0.1.2").andReturn();
        String stolen = cookieValue(first);

        MvcResult rotated = refresh(stolen, ORIGIN, "10.0.1.2").andExpect(status().isOk()).andReturn();
        String legitimate = cookieValue(rotated);
        String legitimateAccess = accessToken(rotated);

        // attacker (or buggy client) presents the already-used token
        refresh(stolen, ORIGIN, "10.0.1.2").andExpect(status().isUnauthorized());

        // ...and now the legitimate successor and its access token are dead as well
        refresh(legitimate, ORIGIN, "10.0.1.2").andExpect(status().isUnauthorized());
        me(legitimateAccess).andExpect(status().isUnauthorized());
    }

    @Test
    void refreshRequiresAKnownTokenAndATrustedOrigin() throws Exception {
        MvcResult login = login(ADMIN_EMAIL, ADMIN_PASSWORD, "10.0.1.3").andReturn();
        String cookie = cookieValue(login);

        // CSRF defence: missing or foreign Origin
        mvc.perform(post("/auth/refresh").cookie(new Cookie(COOKIE, cookie)).with(ip("10.0.1.3")))
                .andExpect(status().isForbidden());
        refresh(cookie, "https://evil.example", "10.0.1.3").andExpect(status().isForbidden());

        // missing / garbage / expired-looking tokens
        mvc.perform(post("/auth/refresh").header(HttpHeaders.ORIGIN, ORIGIN).with(ip("10.0.1.3")))
                .andExpect(status().isUnauthorized());
        refresh("garbage", ORIGIN, "10.0.1.3").andExpect(status().isUnauthorized());
    }

    @Test
    void refreshWorksEvenWhenAStaleBearerHeaderIsSent() throws Exception {
        MvcResult login = login(ADMIN_EMAIL, ADMIN_PASSWORD, "10.0.1.4").andReturn();
        mvc.perform(post("/auth/refresh")
                        .header(HttpHeaders.AUTHORIZATION, "Bearer expired.or.garbage")
                        .header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie(COOKIE, cookieValue(login)))
                        .with(ip("10.0.1.4")))
                .andExpect(status().isOk());
    }

    // ----------------------------------------------------------------- logout

    @Test
    void logoutRevokesAccessAndRefreshTokens() throws Exception {
        MvcResult login = login(ADMIN_EMAIL, ADMIN_PASSWORD, "10.0.2.1").andReturn();
        String cookie = cookieValue(login);
        String access = accessToken(login);
        me(access).andExpect(status().isOk());

        mvc.perform(post("/auth/logout").header(HttpHeaders.ORIGIN, ORIGIN)
                        .cookie(new Cookie(COOKIE, cookie)).with(ip("10.0.2.1")))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, org.hamcrest.Matchers.containsString("Max-Age=0")));

        me(access).andExpect(status().isUnauthorized());
        refresh(cookie, ORIGIN, "10.0.2.1").andExpect(status().isUnauthorized());
    }

    // ---------------------------------------------------- token validation

    @Test
    void tamperedExpiredAndForeignTokensAreRejected() throws Exception {
        String valid = accessToken(login(ADMIN_EMAIL, ADMIN_PASSWORD, "10.0.3.1").andReturn());
        String sid = jwtDecoder.decode(valid).getClaimAsString("sid");
        String sub = jwtDecoder.decode(valid).getSubject();

        // tampered signature
        String[] parts = valid.split("\\.");
        char flipped = parts[2].charAt(0) == 'A' ? 'B' : 'A';
        String tampered = parts[0] + "." + parts[1] + "." + flipped + parts[2].substring(1);
        me(tampered).andExpect(status().isUnauthorized());

        // alg=none, unsigned
        String header = b64("{\"alg\":\"none\",\"typ\":\"JWT\"}");
        String payload = valid.split("\\.")[1];
        me(header + "." + payload + ".").andExpect(status().isUnauthorized());

        // correctly signed with our key, but each is invalid in one respect
        me(forge(sub, sid, "emerald-backend", "emerald-admin", "access", List.of("ADMIN"),
                Instant.now().minusSeconds(3600), Instant.now().minusSeconds(3000))).andExpect(status().isUnauthorized());
        me(forge(sub, sid, "someone-else", "emerald-admin", "access", List.of("ADMIN"),
                Instant.now(), Instant.now().plusSeconds(300))).andExpect(status().isUnauthorized());
        me(forge(sub, sid, "emerald-backend", "another-api", "access", List.of("ADMIN"),
                Instant.now(), Instant.now().plusSeconds(300))).andExpect(status().isUnauthorized());
        me(forge(sub, sid, "emerald-backend", "emerald-admin", "refresh", List.of("ADMIN"),
                Instant.now(), Instant.now().plusSeconds(300))).andExpect(status().isUnauthorized());
        me(forge(sub, UUID.randomUUID().toString(), "emerald-backend", "emerald-admin", "access", List.of("ADMIN"),
                Instant.now(), Instant.now().plusSeconds(300))).andExpect(status().isUnauthorized()); // no such session

        // sanity: the same forge helper with all-valid values is accepted
        me(forge(sub, sid, "emerald-backend", "emerald-admin", "access", List.of("ADMIN"),
                Instant.now(), Instant.now().plusSeconds(300))).andExpect(status().isOk());
    }

    // -------------------------------------------------------- authorization

    @Test
    void adminEndpointsRequireTheAdminRole() throws Exception {
        mvc.perform(get("/admin/ping")).andExpect(status().isUnauthorized());

        String admin = accessToken(login(ADMIN_EMAIL, ADMIN_PASSWORD, "10.0.4.1").andReturn());
        mvc.perform(get("/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + admin))
                .andExpect(status().isOk());

        String sid = jwtDecoder.decode(admin).getClaimAsString("sid");
        String sub = jwtDecoder.decode(admin).getSubject();
        String notAdmin = forge(sub, sid, "emerald-backend", "emerald-admin", "access", List.of("USER"),
                Instant.now(), Instant.now().plusSeconds(300));
        mvc.perform(get("/admin/ping").header(HttpHeaders.AUTHORIZATION, "Bearer " + notAdmin))
                .andExpect(status().isForbidden());
    }

    @Test
    void unlistedPathsAreClosedAndPublicContactStillWorks() throws Exception {
        mvc.perform(get("/something/not/listed")).andExpect(status().isUnauthorized());

        mvc.perform(post("/contact").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Alex\",\"email\":\"alex@example.com\","
                                + "\"message\":\"We need a rebuild of our site.\"}"))
                .andExpect(status().isCreated());
    }

    // ---------------------------------------------------------------- helpers

    private ResultActions login(String email, String password, String ip) throws Exception {
        return mvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON)
                .content(json.writeValueAsString(Map.of("email", email, "password", password)))
                .with(ip(ip)));
    }

    private ResultActions refresh(String cookie, String origin, String ip) throws Exception {
        return mvc.perform(post("/auth/refresh").header(HttpHeaders.ORIGIN, origin)
                .cookie(new Cookie(COOKIE, cookie)).with(ip(ip)));
    }

    private ResultActions me(String accessToken) throws Exception {
        return mvc.perform(get("/auth/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + accessToken));
    }

    private static RequestPostProcessor ip(String address) {
        return request -> {
            request.setRemoteAddr(address);
            return request;
        };
    }

    private String accessToken(MvcResult result) throws Exception {
        JsonNode body = json.readTree(result.getResponse().getContentAsString());
        return body.get("accessToken").asText();
    }

    private static String cookieValue(MvcResult result) {
        String header = result.getResponse().getHeader(HttpHeaders.SET_COOKIE);
        assertThat(header).isNotNull();
        int start = header.indexOf(COOKIE + "=") + COOKIE.length() + 1;
        return header.substring(start, header.indexOf(';', start));
    }

    private static String b64(String value) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(value.getBytes(StandardCharsets.UTF_8));
    }

    /** Signs an arbitrary token with the real key, to prove validation looks at claims and not only the signature. */
    private String forge(String sub, String sid, String issuer, String audience, String use,
                         List<String> roles, Instant issuedAt, Instant expiresAt) {
        SecretKeySpec key = new SecretKeySpec(Base64.getDecoder().decode(secret), "HmacSHA256");
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(issuer).audience(List.of(audience)).subject(sub)
                .issuedAt(issuedAt).expiresAt(expiresAt).id(UUID.randomUUID().toString())
                .claim("roles", roles).claim("sid", sid).claim("token_use", use)
                .build();
        return encoder.encode(JwtEncoderParameters.from(JwsHeader.with(MacAlgorithm.HS256).build(), claims))
                .getTokenValue();
    }
}
