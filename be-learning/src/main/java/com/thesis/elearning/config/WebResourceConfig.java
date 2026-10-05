package com.thesis.elearning.config;

import java.nio.file.Path;
import java.nio.file.Paths;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

/**
 * Serves locally stored learning material.
 *
 * <h2>Why this exists</h2>
 * {@code materials.file_url} is seeded with paths like
 * {@code /uploads/materials/slides_ch1.pdf}. Without a handler the SPA receives a
 * 404 and the PDF viewer renders an empty canvas - a broken-looking feature that
 * is really a missing route.
 *
 * <h2>Why the directory is configurable</h2>
 * The location is read from {@code app.uploads.dir} and falls back to
 * {@code ./uploads}. An operator deploying a jar somewhere other than the project
 * root can point it at a real volume without a rebuild; the default keeps the
 * seeded paths working during development.
 *
 * <h2>Why the handler sits below the context path</h2>
 * Spring MVC matches resource patterns after {@code server.servlet.context-path}
 * strips {@code /api/v1}, so this is reachable at
 * {@code http://localhost:8080/api/v1/uploads/...}. The frontend calls the
 * material path relative to the API base, which keeps the two consistent.
 *
 * <h2>Why traversal is not a concern</h2>
 * The pattern maps only under a single directory and Spring's resource handler
 * rejects paths that resolve outside it, so {@code ../} cannot escape to the rest
 * of the filesystem.
 */
@Configuration
public class WebResourceConfig implements WebMvcConfigurer {

    /** Default location, relative to the process working directory. */
    private static final String DEFAULT_UPLOAD_DIR = "./uploads";

    /** URL prefix the SPA requests materials under. */
    private static final String URL_PATTERN = "/uploads/**";

    private final String uploadLocation;

    public WebResourceConfig(final org.springframework.core.env.Environment environment) {
        final String configured = environment.getProperty("app.uploads.dir", DEFAULT_UPLOAD_DIR);
        final Path root = Paths.get(configured).toAbsolutePath().normalize();
        // Trailing separator matters: without it Spring treats the last path
        // segment as a resource name prefix rather than as the directory itself.
        this.uploadLocation = root.toUri().toString();
    }

    @Override
    public void addResourceHandlers(final ResourceHandlerRegistry registry) {
        registry.addResourceHandler(URL_PATTERN)
                .addResourceLocations(uploadLocation)
                .setCachePeriod(3600);
    }

    /** Exposed for diagnostics and for the smoke test. */
    public String uploadLocation() {
        return uploadLocation;
    }
}