package com.thesis.elearning.modules.admin.service;

import com.thesis.elearning.common.exception.AppException;
import com.thesis.elearning.common.exception.ErrorCode;
import com.thesis.elearning.modules.auth.entity.Role;
import com.thesis.elearning.modules.auth.entity.User;
import com.thesis.elearning.modules.auth.repository.RoleRepository;
import com.thesis.elearning.modules.auth.repository.UserRepository;
import com.thesis.elearning.modules.auth.dto.UserProfileDto;
import java.util.Set;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Account provisioning for administrators.
 *
 * <p>This is the only place in the system that may grant an authority other
 * than {@code ROLE_STUDENT}. Public self-registration deliberately has no role
 * field: accepting one from an anonymous caller would let anyone POST
 * {@code requestedRole: "ROLE_ADMIN"} and mint themselves an administrator
 * (CWE-269, Improper Privilege Management).
 */
@Service
public class AdminUserService {

    /** Authorities an administrator may hand out. */
    private static final Set<String> ASSIGNABLE_ROLES =
            Set.of("ROLE_STUDENT", "ROLE_TEACHER", "ROLE_ADMIN");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminUserService(
            final UserRepository userRepository,
            final RoleRepository roleRepository,
            final PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Creates an account with an explicit role.
     *
     * @param requestedRole validated against {@link #ASSIGNABLE_ROLES} so a typo
     *                      cannot silently create a role-less account that can
     *                      log in but reach nothing.
     */
    @Transactional
    public UserProfileDto createUser(
            final String username,
            final String email,
            final String rawPassword,
            final String fullName,
            final String requestedRole) {

        final String normalisedRole =
                requestedRole == null ? "ROLE_STUDENT" : requestedRole.trim().toUpperCase();

        if (!ASSIGNABLE_ROLES.contains(normalisedRole)) {
            throw new AppException(
                    ErrorCode.BAD_REQUEST,
                    "Vai trò không hợp lệ. Chỉ chấp nhận ROLE_STUDENT, ROLE_TEACHER hoặc ROLE_ADMIN.");
        }

        final String trimmedUsername = username.trim();
        final String trimmedEmail = email.trim();

        if (userRepository.existsByUsername(trimmedUsername)) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Tên đăng nhập đã tồn tại");
        }
        if (userRepository.existsByEmail(trimmedEmail)) {
            throw new AppException(ErrorCode.DUPLICATE_RESOURCE, "Địa chỉ email đã được sử dụng");
        }

        final Role role =
                roleRepository
                        .findByRoleCode(normalisedRole)
                        .orElseThrow(
                                () ->
                                        new AppException(
                                                ErrorCode.BAD_REQUEST,
                                                "Vai trò chưa được thiết lập trong hệ thống"));

        final User created =
                userRepository.save(
                        User.builder()
                                .username(trimmedUsername)
                                .email(trimmedEmail)
                                .passwordHash(passwordEncoder.encode(rawPassword))
                                .fullName(fullName.trim())
                                .active(true)
                                .deleted(false)
                                .roles(Set.of(role))
                                .build());

        return UserProfileDto.from(created);
    }

    /** Enabled/disabled toggle; disabled accounts cannot sign in. */
    @Transactional
    public UserProfileDto setActive(final Long userId, final boolean active) {
        final User user =
                userRepository
                        .findById(userId)
                        .orElseThrow(() -> new AppException(ErrorCode.RESOURCE_NOT_FOUND));

        user.setActive(active);
        return UserProfileDto.from(userRepository.save(user));
    }
}