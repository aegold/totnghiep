package com.thesis.elearning.config.security;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Security wiring tests for the filter chain.
 *
 * <p>The subject under test <em>is</em> the chain: which routes are public,
 * which are role-gated, and what a rejection looks like on the wire. Role
 * separation is asserted against the real {@code /admin/users} endpoint rather
 * than a purpose-built probe, so production code carries nothing that exists
 * only for testing.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
class SecurityRbacTest {

    @Autowired private MockMvc mockMvc;

    private static Authentication auth(final String authority) {
        return new UsernamePasswordAuthenticationToken(
                "42", null, List.of(new SimpleGrantedAuthority(authority)));
    }

    @Test
    @DisplayName("health is reachable without a token")
    void healthIsPublic() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }

    @Test
    @DisplayName("a protected route without a token returns the Vietnamese 401 envelope")
    void protectedWithoutToken() throws Exception {
        mockMvc.perform(get("/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorCode").value("UNAUTHORIZED"))
                .andExpect(jsonPath("$.message").value("Yêu cầu xác thực tài khoản để tiếp tục"));
    }

    @Test
    @DisplayName("a forged bearer token yields 401, not 500")
    void forgedTokenIsRejectedCleanly() throws Exception {
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer not.a.jwt"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").exists());
    }

    @Test
    @DisplayName("a STUDENT is refused /admin/** with the Vietnamese 403 envelope")
    void studentRefusedFromAdminSubtree() throws Exception {
        mockMvc.perform(get("/admin/users/roles").with(authentication(auth("ROLE_STUDENT"))))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.errorCode").value("FORBIDDEN"))
                .andExpect(jsonPath("$.message")
                        .value("Bạn không có quyền truy cập vào tài nguyên này"));
    }

    @Test
    @DisplayName("a TEACHER is refused /admin/** too")
    void teacherRefusedFromAdminSubtree() throws Exception {
        mockMvc.perform(get("/admin/users/roles").with(authentication(auth("ROLE_TEACHER"))))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("an ADMIN reaches /admin/**")
    void adminAllowed() throws Exception {
        mockMvc.perform(get("/admin/users/roles").with(authentication(auth("ROLE_ADMIN"))))
                .andExpect(status().isOk());
    }

    /**
     * Regression guard for the context-path trap.
     *
     * <p>{@code server.servlet.context-path} is {@code /api/v1}, and Spring
     * Security matches the path <em>after</em> it. If someone writes
     * {@code requestMatchers("/api/v1/auth/login")}, the rule silently stops
     * matching and login 401s for everyone - which reads like a credentials
     * bug rather than a routing one. This asserts the relative form is the one
     * that actually works.
     */
    @Test
    @DisplayName("matchers are context-path relative, not /api/v1-prefixed")
    void matchersAreContextPathRelative() throws Exception {
        // Reaches the controller layer (not 401) => the relative matcher matched.
        mockMvc.perform(get("/health")).andExpect(status().isOk());
    }
}