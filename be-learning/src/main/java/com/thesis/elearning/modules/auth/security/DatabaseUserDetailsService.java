package com.thesis.elearning.modules.auth.security;

import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.auth.repository.UserRepository;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Adapts the {@code users} table to Spring Security.
 *
 * <p>Authorities come from {@code roles.role_code} through the
 * {@code user_roles} join table and are already in Spring's {@code ROLE_*}
 * form, so they are granted verbatim. Renaming them here would silently break
 * every {@code hasRole} check.
 */
@Service
public class DatabaseUserDetailsService implements UserDetailsService {

    private final UserRepository userRepository;

    public DatabaseUserDetailsService(final UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(final String usernameOrEmail)
            throws UsernameNotFoundException {

        final User user =
                userRepository
                        .findByUsernameOrEmail(usernameOrEmail)
                        .orElseThrow(
                                () -> new UsernameNotFoundException("Không tìm thấy tài khoản"));

        return org.springframework.security.core.userdetails.User.builder()
                .username(user.getUsername())
                .password(user.getPasswordHash())
                /*
                 * Spring Security 6 replaced the fluent `enabled(boolean)` with
                 * `disabled(boolean)`, so the sense is inverted here.
                 *
                 * Soft deletion needs no flag: @SQLRestriction on the entity
                 * hides deleted rows from this query entirely.
                 */
                .disabled(!user.isActive())
                .authorities(
                        user.getRoles().stream()
                                .map(role -> new SimpleGrantedAuthority(role.getRoleCode()))
                                .toList())
                .build();
    }
}