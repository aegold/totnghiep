package com.thesis.elearning.modules.auth.repository;

import com.thesis.elearning.modules.auth.entity.User;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

/**
 * Account lookups used by the authentication flow.
 *
 * <p>Soft-deleted rows are excluded automatically by the {@code @SQLRestriction}
 * declared on {@link User}.
 */
public interface UserRepository extends JpaRepository<User, Long> {

    Optional<User> findByUsername(String username);

    Optional<User> findByEmail(String email);

    /**
     * Sign-in accepts either identifier; the login form labels its field
     * "Tên đăng nhập hoặc email" and the client sends a single {@code username}
     * value, so the server resolves both.
     *
     * <p>Written as an explicit query rather than {@code findByUsernameOrEmail}
     * so the OR can reuse the {@code user_id} index instead of forcing a
     * full scan over two unique columns.
     */
    @Query("SELECT u FROM User u WHERE u.username = :identifier OR u.email = :identifier")
    Optional<User> findByUsernameOrEmail(@Param("identifier") String identifier);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);
}