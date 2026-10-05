package com.thesis.elearning.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDateTime;
import java.util.LinkedHashSet;
import java.util.Set;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.SQLDelete;
import org.hibernate.annotations.SQLRestriction;

/**
 * Application account, mapped 1:1 onto the pre-existing {@code users} table.
 *
 * <p>Column names are declared explicitly rather than inferred from field names
 * because the table does NOT follow a single naming convention: the primary key
 * is {@code user_id} (not {@code id}), and the active flag is
 * {@code is_active} (not {@code enabled}).
 *
 * <p>{@code @SQLDelete} turns {@code delete()} into an UPDATE, and
 * {@code @SQLRestriction} appends {@code is_deleted = 0} to every query, so a
 * soft-deleted row can never be loaded again by ordinary repository calls.
 */
@Entity
@Table(name = "users")
@SQLDelete(
        sql = "UPDATE users SET is_deleted = 1, deleted_at = CURRENT_TIMESTAMP "
                + "WHERE user_id = ?")
@SQLRestriction("is_deleted = 0")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class User {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "user_id")
    private Long userId;

    @Column(name = "username", nullable = false, length = 50, unique = true)
    private String username;

    @Column(name = "email", nullable = false, length = 100, unique = true)
    private String email;

    /** BCrypt hash. The plaintext password never leaves the request scope. */
    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "full_name", nullable = false, length = 100)
    private String fullName;

    @Column(name = "avatar_url", length = 500)
    private String avatarUrl;

    /**
     * Whether the account may authenticate.
     *
     * <p>Distinct from soft deletion: a deactivated account still exists (its
     * authored questions and submissions must remain resolvable) but must not be
     * able to sign in.
     */
    @Column(name = "is_active", nullable = false)
    @Builder.Default
    private boolean active = true;

    @Column(name = "is_deleted", nullable = false)
    @Builder.Default
    private boolean deleted = false;

    @Column(name = "deleted_at")
    private LocalDateTime deletedAt;

    /** Database default {@code CURRENT_TIMESTAMP}; never written by the app. */
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Database default plus {@code ON UPDATE}; never written by the app. */
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "updated_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime updatedAt;

    /**
     * Authorities granted to this account.
     *
     * <p>EAGER because Spring Security needs them on every authenticated
     * request to build the {@code Authentication}; a lazy proxy would trigger a
     * session lookup outside the transaction on each request. The table is tiny
     * (three rows), so the join is cheaper than the alternative.
     */
    @ManyToMany(fetch = FetchType.EAGER)
    @JoinTable(
            name = "user_roles",
            joinColumns = @JoinColumn(name = "user_id"),
            inverseJoinColumns = @JoinColumn(name = "role_id"))
    @Builder.Default
    private Set<Role> roles = new LinkedHashSet<>();

    /** Whether this account may authenticate at all. */
    public boolean isLoginAllowed() {
        return active && !deleted;
    }
}
