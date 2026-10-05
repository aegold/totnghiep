package com.thesis.elearning.config.properties;

import jakarta.validation.constraints.NotEmpty;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Type-safe binding for the {@code app.cors.*} configuration block.
 *
 * <p>Origins are an explicit allow-list rather than a wildcard: the client sends
 * credentials (cookies plus an Authorization header), and the CORS spec forbids
 * pairing {@code Access-Control-Allow-Credentials: true} with {@code *}.
 *
 * @param allowedOrigins   Exact origins permitted to send credentialed requests.
 * @param allowedMethods   HTTP verbs permitted.
 * @param allowedHeaders   Request headers the browser may send.
 * @param exposedHeaders   Response headers readable by client JavaScript.
 * @param allowCredentials Whether cookies/authorization may be sent.
 * @param maxAge           How long a preflight result may be cached, in seconds.
 */
@Validated
@ConfigurationProperties(prefix = "app.cors")
public record CorsProperties(
        @NotEmpty List<String> allowedOrigins,
        @NotEmpty List<String> allowedMethods,
        @NotEmpty List<String> allowedHeaders,
        List<String> exposedHeaders,
        boolean allowCredentials,
        long maxAge) {

    public CorsProperties {
        exposedHeaders = exposedHeaders == null ? List.of() : List.copyOf(exposedHeaders);
    }
}