package com.thesis.elearning.config.security;

import com.thesis.elearning.modules.auth.security.DatabaseUserDetailsService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationProvider;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

/**
 * Stateless JWT security for the whole API.
 *
 * <h2>Why every matcher below is context-path relative</h2>
 * {@code server.servlet.context-path} is {@code /api/v1}, and Spring Security
 * matches against the path <em>after</em> the context path. Writing
 * {@code /api/v1/auth/login} here would silently never match and the endpoint
 * would answer 401 for every caller - a failure that looks like a credentials
 * problem rather than a routing one. Everything below is therefore relative:
 * {@code /auth/login}.
 *
 * <h2>Why there is no role hierarchy</h2>
 * A {@code ROLE_ADMIN > ROLE_TEACHER > ROLE_STUDENT} hierarchy would make
 * {@code hasRole('STUDENT')} true for teachers, breaking the isolation the client
 * depends on: it mounts three independent subtrees ({@code /student/**},
 * {@code /teacher/**}, {@code /admin/**}) and routes users by role. Under a
 * hierarchy a teacher could read student-only resources. Explicit per-subtree
 * rules keep the boundaries where the UI expects them.
 *
 * <h2>Why learning material is public</h2>
 * {@code /uploads/**} is served to the browser by an iframe and by the pdf.js
 * worker, neither of which attaches an Authorization header. Gating the route
 * would render the PDF viewer as a blank canvas while every other test still
 * passed. This is acceptable for locally stored courseware; if material must stay
 * private in production, replace it with short-lived signed URLs.
 *
 * <h2>Ordering</h2>
 * Rules are evaluated top to bottom and the first match wins, so the public block
 * must precede {@code anyRequest()}.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final DatabaseUserDetailsService userDetailsService;
    private final PasswordEncoder passwordEncoder;
    private final CustomAuthenticationEntryPoint authenticationEntryPoint;
    private final CustomAccessDeniedHandler accessDeniedHandler;

    public SecurityConfig(
            final JwtAuthenticationFilter jwtAuthenticationFilter,
            final DatabaseUserDetailsService userDetailsService,
            final PasswordEncoder passwordEncoder,
            final CustomAuthenticationEntryPoint authenticationEntryPoint,
            final CustomAccessDeniedHandler accessDeniedHandler) {
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.userDetailsService = userDetailsService;
        this.passwordEncoder = passwordEncoder;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.accessDeniedHandler = accessDeniedHandler;
    }

    /**
     * Wires username/password authentication against the {@code users} table.
     *
     * <p>Declared explicitly to suppress Boot's auto-configuration, which would
     * otherwise create a second provider and warn at startup.
     *
     * <p>{@code hideUserNotFoundExceptions} collapses "no such account" into "bad
     * credentials" so {@code /auth/login} cannot be used to enumerate users.
     */
    @Bean
    public AuthenticationProvider authenticationProvider() {
        final DaoAuthenticationProvider provider = new DaoAuthenticationProvider();
        provider.setUserDetailsService(userDetailsService);
        provider.setPasswordEncoder(passwordEncoder);
        provider.setHideUserNotFoundExceptions(true);
        return provider;
    }

    /** The one place the {@code AuthenticationManager} is exposed to Boot. */
    @Bean
    public AuthenticationManager authenticationManager(
            final AuthenticationConfiguration configuration) throws Exception {
        return configuration.getAuthenticationManager();
    }

    /** The filter chain: CORS, stateless, public routes, then role-gated subtrees. */
    @Bean
    public SecurityFilterChain filterChain(final HttpSecurity http) throws Exception {
        http
                // CorsConfig is consulted before authentication so preflight
                // requests are not rejected as unauthenticated.
                .cors(cors -> {})
                // No cookies are used, so there is no CSRF surface to protect.
                .csrf(csrf -> csrf.disable())
                .sessionManagement(
                        session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(
                        auth -> auth
                                // Preflight must never require a token.
                                .requestMatchers(HttpMethod.OPTIONS, "/**")
                                .permitAll()
                                // Liveness probes: read-only, expose no user data.
                                .requestMatchers(
                                        "/health",
                                        "/actuator/health",
                                        "/actuator/info")
                                .permitAll()
                                // Public area: catalogue and reference data.
                                .requestMatchers("/public/**")
                                .permitAll()
                                /*
                                 * Credential exchange. /auth/me stays protected
                                 * because it re-validates a persisted session and
                                 * must fail when the token is absent.
                                 * /auth/logout is public by design: the access
                                 * token has usually expired by sign-out time, and
                                 * a 401 there would skip revocation.
                                 */
                                .requestMatchers(
                                        "/auth/login",
                                        "/auth/register",
                                        "/auth/refresh",
                                        "/auth/logout",
                                        "/uploads/**")
                                .permitAll()
                                // Role-gated subtrees mirroring the client router.
                                .requestMatchers("/admin/**")
                                .hasRole("ADMIN")
                                /*
                                 * /teacher/** accepts ADMIN as well as TEACHER.
                                 * Method-level @PreAuthorize already expresses this,
                                 * but URL rules are evaluated by the filter chain
                                 * BEFORE method security runs, so a matcher pinned to
                                 * hasRole("TEACHER") would reject the admin at the
                                 * filter and the annotation would never be reached,
                                 * silently making the annotation a lie.
                                 */
                                .requestMatchers("/teacher/**")
                                .hasAnyRole("TEACHER", "ADMIN")
                                // The exam-runner aliases for the student subtree.
                                .requestMatchers("/student/**", "/exam-runs/**", "/exam-submissions/**")
                                .hasRole("STUDENT")
                                .anyRequest()
                                .authenticated())
                .authenticationProvider(authenticationProvider())
                .addFilterBefore(
                        jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class)
                .exceptionHandling(
                        handling ->
                                handling.authenticationEntryPoint(authenticationEntryPoint)
                                        .accessDeniedHandler(accessDeniedHandler))
                // Stateless bearer-token API: no browser session, no Basic/form login.
                .httpBasic(basic -> basic.disable())
                .formLogin(form -> form.disable());

        return http.build();
    }
}