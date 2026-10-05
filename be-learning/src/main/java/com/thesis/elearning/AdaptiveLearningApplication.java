package com.thesis.elearning;

import com.thesis.elearning.config.properties.JwtProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;

/**
 * Entry point for the Adaptive Learning core API.
 *
 * <p>The servlet context path is set in {@code application.yml} to
 * {@code /api/v1}, which is what the React client's Axios {@code baseURL}
 * targets. Changing either side independently will silently break every call.
 */
@SpringBootApplication
@EnableConfigurationProperties(JwtProperties.class)
public class AdaptiveLearningApplication {

    public static void main(final String[] args) {
        SpringApplication.run(AdaptiveLearningApplication.class, args);
    }
}