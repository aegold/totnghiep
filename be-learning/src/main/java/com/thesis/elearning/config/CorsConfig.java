package com.thesis.elearning.config;

import com.thesis.elearning.config.properties.CorsProperties;
import java.util.List;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * CORS wiring for the React client on {@code http://localhost:5173}.
 *
 * <p>Exposed twice on purpose, because the two layers solve different problems:
 * <ul>
 *   <li>{@link #corsConfigurationSource()} is consumed by Spring Security, so
 *       preflight {@code OPTIONS} requests are answered <em>before</em> the
 *       filter chain would otherwise reject them with a 401. Without this,
 *       every cross-origin call fails at the preflight and the browser reports
 *       an opaque network error rather than the real status.</li>
 *   <li>{@link #addCorsMappings} covers the MVC dispatcher for handler-level
 *       resolution.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(CorsProperties.class)
public class CorsConfig implements WebMvcConfigurer {

    private final CorsProperties properties;

    public CorsConfig(final CorsProperties properties) {
        this.properties = properties;
    }

    /** Spring Security's entry point; answers preflight before authentication. */
    @Bean
    public UrlBasedCorsConfigurationSource corsConfigurationSource() {
        final CorsConfiguration configuration = new CorsConfiguration();

        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(properties.allowedMethods());
        configuration.setAllowedHeaders(properties.allowedHeaders());
        configuration.setExposedHeaders(properties.exposedHeaders());
        configuration.setAllowCredentials(properties.allowCredentials());
        configuration.setMaxAge(properties.maxAge());

        final UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /** MVC-dispatcher equivalent, for handlers resolved outside the filter chain. */
    @Override
    public void addCorsMappings(final CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOrigins(properties.allowedOrigins().toArray(String[]::new))
                .allowedMethods(properties.allowedMethods().toArray(String[]::new))
                .allowedHeaders(properties.allowedHeaders().toArray(String[]::new))
                .exposedHeaders(properties.exposedHeaders().toArray(String[]::new))
                .allowCredentials(properties.allowCredentials())
                .maxAge(properties.maxAge());
    }

    /**
     * Baseline policy applied to any future endpoint that is not covered by an
     * explicit mapping. Exposed for tests and for reuse in {@code SecurityConfig}.
     */
    public CorsConfiguration defaultCorsConfiguration() {
        final CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(properties.allowedOrigins());
        configuration.setAllowedMethods(
                List.of(
                        HttpMethod.GET.name(),
                        HttpMethod.POST.name(),
                        HttpMethod.PUT.name(),
                        HttpMethod.DELETE.name(),
                        HttpMethod.PATCH.name(),
                        HttpMethod.OPTIONS.name()));
        configuration.setAllowedHeaders(
                List.of(
                        HttpHeaders.ACCEPT,
                        HttpHeaders.AUTHORIZATION,
                        HttpHeaders.CONTENT_TYPE,
                        HttpHeaders.ORIGIN,
                        "X-Requested-With",
                        "X-Correlation-Id"));
        configuration.setExposedHeaders(List.of("X-Correlation-Id"));
        configuration.setAllowCredentials(true);
        return configuration;
    }
}