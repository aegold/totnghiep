package com.thesis.elearning.modules.auth.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.ColumnDefault;
import java.time.LocalDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * A persisted refresh token, mapped onto the pre-existing
 * {@code refresh_tokens} table.
 *
 * <p>## Why the token is hashed
 * The column is named {@code token} and is unique, but this entity stores a
 * SHA-256 hex digest of the issued token, never the token itself. A leaked
 * database row would otherwise hand an attacker working refresh tokens for
 * every live session, which fully defeats the 7-day rotation window.
 *
 * <p>A SHA-256 hex digest is 64 characters, comfortably inside the existing
 * {@code varchar(255)}, so the pre-existing schema is reused unchanged. SHA-256
 * (not bcrypt) is the correct choice here: lookup is by exact match on an
 * indexed unique column, so there is no brute-force path where a slow KDF would
 * add value.
 *
 * <p>Server-side storage also makes revocation possible, which is exactly why a
 * refresh token must not be a self-contained JWT.
 */
@Entity
@Table(name = "refresh_tokens")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RefreshToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "token_id")
    private Long tokenId;

    /** FK is lazy: loading a token never needs the whole user graph. */
    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    /** SHA-256 hex digest of the issued token. */
    @Column(name = "token", nullable = false, length = 255, unique = true)
    private String token;

    @Column(name = "expiry_date", nullable = false)
    private LocalDateTime expiryDate;

    @Column(name = "revoked", nullable = false)
    @Builder.Default
    private boolean revoked = false;

    /**
     * Database default, declared so the generated schema matches MySQL''s.
     *
     * <p>MySQL supplies {@code DEFAULT CURRENT_TIMESTAMP} for this column, but the
     * test profile builds its schema from the entities, and a column that is both
     * {@code insertable = false} and has no declared default becomes NOT NULL with
     * nothing to fill it - so every insert failed under the test profile while
     * working fine against the real database. Declaring the default here keeps the
     * two schemas equivalent.
     */
    @ColumnDefault("CURRENT_TIMESTAMP")
    @Column(name = "created_at", nullable = false, insertable = false, updatable = false)
    private LocalDateTime createdAt;

    /** Whether this token may still be exchanged for a new access token. */
    public boolean isUsableAt(final LocalDateTime now) {
        return !revoked && expiryDate.isAfter(now);
    }
}