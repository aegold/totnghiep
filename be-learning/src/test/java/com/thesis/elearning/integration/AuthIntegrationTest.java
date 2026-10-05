package com.thesis.elearning.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

/** Sign-in for every seeded role, plus the refusal an unknown credential gets. */
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "spring.datasource.url=jdbc:h2:mem:seed_auth;MODE=MySQL;DB_CLOSE_DELAY=-1;DATABASE_TO_LOWER=TRUE",
            "app.seed.enabled=true"
        })
class AuthIntegrationTest extends SeededIntegrationTest {

    @ParameterizedTest(name = "{0} can sign in and is granted {2}")
    @CsvSource({
        "admin,    admin123, ROLE_ADMIN",
        "teacher1, 123456,   ROLE_TEACHER",
        "teacher2, 123456,   ROLE_TEACHER",
        "student1, 123456,   ROLE_STUDENT",
        "student5, 123456,   ROLE_STUDENT",
    })
    @DisplayName("every seeded account authenticates with its documented password")
    void seededAccountsAuthenticate(
            final String username, final String password, final String role) throws Exception {
        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"username":"%s","password":"%s"}
                                        """
                                                .formatted(username, password)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.user.username").value(username))
                .andExpect(jsonPath("$.data.user.roles[0]").value(role))
                .andExpect(jsonPath("$.data.tokens.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.data.tokens.refreshToken").isNotEmpty());
    }

    @Test
    @DisplayName("a wrong password is refused in Vietnamese, with no hint that the account exists")
    void wrongPasswordIsRefused() throws Exception {
        mockMvc.perform(
                        post("/auth/login")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"username":"teacher1","password":"khong-dung"}
                                        """))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_CREDENTIALS"))
                .andExpect(
                        jsonPath("$.message")
                                .value("T\u00EAn \u0111\u0103ng nh\u1EADp ho\u1EB7c m\u1EADt kh\u1EA9u kh\u00F4ng ch\u00EDnh x\u00E1c."));
    }

    @Test
    @DisplayName("an unknown account and a wrong password are indistinguishable")
    void unknownAccountLooksLikeWrongPassword() throws Exception {
        final String unknown =
                mockMvc.perform(
                                post("/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"username":"khong-ton-tai","password":"123456"}
                                                """))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();

        assertThat(json(unknown).path("errorCode").asText()).isEqualTo("INVALID_CREDENTIALS");
        assertThat(json(unknown).path("message").asText()).contains("kh\u00F4ng ch\u00EDnh x\u00E1c");
    }

    @Test
    @DisplayName("a refresh token is single-use: rotating it invalidates the original")
    void refreshTokenIsSingleUse() throws Exception {
        final String body =
                mockMvc.perform(
                                post("/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(
                                                """
                                                {"username":"student1","password":"123456"}
                                                """))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        final String refresh = json(body).path("data").path("tokens").path("refreshToken").asText();

        // First exchange succeeds and mints a new pair.
        mockMvc.perform(
                        post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"refreshToken":"%s"}
                                        """
                                                .formatted(refresh)))
                .andExpect(status().isOk());

        // Replaying the original must now fail - the property rotation exists for.
        mockMvc.perform(
                        post("/auth/refresh")
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {"refreshToken":"%s"}
                                        """
                                                .formatted(refresh)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.errorCode").value("INVALID_REFRESH_TOKEN"));
    }
}