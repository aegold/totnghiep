package com.thesis.elearning.integration;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Shared plumbing for the suites that run against the seeded dataset.
 *
 * <h2>Why every subclass names its own H2 database</h2>
 * The {@code test} profile uses {@code DB_CLOSE_DELAY=-1}, which keeps one in-memory
 * database alive for the whole JVM - shared across every Spring context. A suite
 * that submits a paper would therefore leave rows behind that a later analytics
 * suite would count, turning a legitimate ordering difference into a red build. A
 * distinct URL per class forces a distinct context and a distinct database, so the
 * suites are independent in either order.
 *
 * <h2>Why the seeder is re-enabled here</h2>
 * It is disabled under {@code test} by default so that the 255 pre-existing tests
 * are not perturbed by eight BCrypt hashes and a curriculum they did not ask for.
 * These suites are the ones that actually want the data.
 */
@SpringBootTest
@AutoConfigureMockMvc
abstract class SeededIntegrationTest {

    @Autowired protected MockMvc mockMvc;

    @Autowired protected ObjectMapper objectMapper;

    /**
     * Signs in and returns the bearer token.
     *
     * <p>Drives the real login endpoint rather than faking an authentication, so a
     * seeded password that does not actually verify fails here instead of hiding
     * behind a mocked principal.
     */
    protected String tokenFor(final String username, final String password) throws Exception {
        final String body =
                """
                {"username":"%s","password":"%s"}
                """
                        .formatted(username, password);
        final String response =
                mockMvc.perform(
                                post("/auth/login")
                                        .contentType(MediaType.APPLICATION_JSON)
                                        .content(body))
                        .andReturn()
                        .getResponse()
                        .getContentAsString();
        return objectMapper.readTree(response).path("data").path("tokens").path("accessToken").asText();
    }

    protected JsonNode json(final String raw) throws Exception {
        return objectMapper.readTree(raw);
    }
}