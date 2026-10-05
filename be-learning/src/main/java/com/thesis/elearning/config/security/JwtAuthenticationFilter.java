package com.thesis.elearning.config.security;

import com.thesis.elearning.common.exception.ErrorCode;
import io.jsonwebtoken.Claims;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.List;
import java.util.Optional;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Authenticates a request from its {@code Authorization: Bearer} header.
 *
 * <p>## Why authorities are read from the token, not the database
 * A lookup on every request would defeat the purpose of a stateless access
 * token. The trade-off is that a role change takes effect only after the token
 * expires - bounded at 15 minutes, which is why the lifetime is kept short.
 *
 * <p>## Why failures are recorded instead of thrown
 * A missing or invalid token is not an error here: the filter leaves the context
 * anonymous and lets the authorization rules decide. Rejecting inside the filter
 * would make the public endpoints (login, register, refresh, health) unreachable.
 * The reason is stashed as a request attribute so
 * {@link CustomAuthenticationEntryPoint} can explain what went wrong rather than
 * returning one generic message for every cause.
 */
@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    static final String HEADER = "Authorization";
    static final String PREFIX = "Bearer ";

    /** Request attribute carrying the {@link ErrorCode} that blocked the request. */
    static final String ATTR_FAILURE = "elearning.auth.failure";

    private final JwtTokenProvider tokenProvider;

    public JwtAuthenticationFilter(final JwtTokenProvider tokenProvider) {
        this.tokenProvider = tokenProvider;
    }

    @Override
    protected void doFilterInternal(
            @NonNull final HttpServletRequest request,
            @NonNull final HttpServletResponse response,
            @NonNull final FilterChain filterChain)
            throws ServletException, IOException {

        final String header = request.getHeader(HEADER);

        if (header != null
                && header.startsWith(PREFIX)
                && SecurityContextHolder.getContext().getAuthentication() == null) {

            final String token = header.substring(PREFIX.length());
            final Optional<Claims> parsed = tokenProvider.parseAccessToken(token);

            if (parsed.isPresent() && parsed.get().getSubject() != null) {
                final Claims claims = parsed.get();
                final List<String> roles = readRoles(claims);

                final var authentication =
                        new UsernamePasswordAuthenticationToken(
                                claims.getSubject(), null,
                                roles.stream().map(SimpleGrantedAuthority::new).toList());

                authentication.setDetails(
                        new WebAuthenticationDetailsSource().buildDetails(request));
                SecurityContextHolder.getContext().setAuthentication(authentication);
            } else {
                // Leave the context anonymous; the entry point turns this into a
                // 401 with the right Vietnamese explanation.
                request.setAttribute(ATTR_FAILURE, tokenProvider.classifyFailure(token));
            }
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Reads the authorities claim.
     *
     * <p>A token minted by an older build might lack the claim entirely, so a
     * missing or wrongly typed value yields no authorities rather than throwing
     * - the request simply fails the authorization rules downstream.
     */
    @SuppressWarnings("unchecked")
    private static List<String> readRoles(final Claims claims) {
        final Object raw = claims.get(JwtTokenProvider.CLAIM_ROLES);
        if (!(raw instanceof List<?> list)) {
            return List.of();
        }
        return list.stream().map(String::valueOf).toList();
    }

    /** Exposed for {@link CustomAccessDeniedHandler} and tests. */
    static ErrorCode failureOf(final HttpServletRequest request) {
        final Object attribute = request.getAttribute(ATTR_FAILURE);
        return attribute instanceof ErrorCode code ? code : null;
    }
}