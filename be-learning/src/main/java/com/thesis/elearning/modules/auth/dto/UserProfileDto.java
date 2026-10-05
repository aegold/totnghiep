package com.thesis.elearning.modules.auth.dto;

import com.thesis.elearning.modules.auth.entity.User;
import java.util.List;

/**
 * The authenticated principal, projected for the client.
 *
 * <p>Mirrors the {@code User} interface in {@code src/types/auth.ts} field for
 * field. {@code roles} carries the raw {@code role_code} values
 * ({@code ROLE_STUDENT}, …) because the client switches on those exact strings
 * in {@code hasRole} and in route resolution.
 *
 * <p>The password hash is structurally absent, so it cannot leak through a
 * forgotten getter or a future {@code BeanUtils.copyProperties}.
 *
 * @param userId    primary key, matches {@code users.user_id}.
 * @param username  login handle.
 * @param email     contact address, unique in the schema.
 * @param fullName  display name.
 * @param avatarUrl nullable, matches {@code users.avatar_url}.
 * @param roles     authorities, sorted for a stable response body.
 */
public record UserProfileDto(
        Long userId,
        String username,
        String email,
        String fullName,
        String avatarUrl,
        List<String> roles) {

    /** Projects an entity onto the wire shape. */
    public static UserProfileDto from(final User user) {
        return new UserProfileDto(
                user.getUserId(),
                user.getUsername(),
                user.getEmail(),
                user.getFullName(),
                user.getAvatarUrl(),
                user.getRoles().stream()
                        .map(role -> role.getRoleCode())
                        .sorted()
                        .toList());
    }
}