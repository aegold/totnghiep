package com.thesis.elearning.modules.auth.repository;

import com.thesis.elearning.modules.auth.entity.RefreshToken;
import jakarta.persistence.LockModeType;
import java.time.LocalDateTime;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

    /**
     * Unlocked lookup, for paths that only need to know whether a token exists.
     *
     * <p>Sign-out uses this: revoking is idempotent, so there is no read-then-write
     * decision to lose a race over.
     */
    Optional<RefreshToken> findByToken(String token);

    /**
     * Locking lookup, used by rotation.
     *
     * <h2>Why rotation must lock</h2>
     * Rotation is a read-decide-write: read the row, check it is still usable, mark
     * it revoked, save. Without a lock two concurrent refreshes carrying the SAME
     * token both read {@code revoked = false}, both pass the check, and both mint a
     * full session - which defeats the property rotation exists to provide, because
     * a stolen token replayed right after the legitimate client refreshes would now
     * succeed instead of being rejected.
     *
     * <p>{@code PESSIMISTIC_WRITE} takes a {@code SELECT ... FOR UPDATE} held until
     * the surrounding transaction commits. The second caller therefore blocks, and
     * when it resumes it re-reads the row and observes {@code revoked = true}.
     *
     * <p>An optimistic version column could not do this: both threads would read
     * version N and the loser would fail on commit with an
     * {@code OptimisticLockException}, which would surface as a 500 rather than the
     * 401 the caller should see.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT t FROM RefreshToken t WHERE t.token = :token")
    Optional<RefreshToken> findByTokenForUpdate(@Param("token") String token);

    /**
     * Revokes every live token for a user.
     *
     * <p>Bulk update because it touches rows no caller will read back; returning
     * the entities would force a pointless re-read of each token's user graph.
     */
    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("UPDATE RefreshToken t SET t.revoked = true "
            + "WHERE t.user.userId = :userId AND t.revoked = false")
    int revokeAllForUser(@Param("userId") Long userId);

    /** Purges rows that can no longer be exchanged; safe to run on a schedule. */
    @Modifying
    @Query("DELETE FROM RefreshToken t WHERE t.expiryDate < :cutoff")
    int deleteExpiredBefore(@Param("cutoff") LocalDateTime cutoff);

    long countByUserUserIdAndRevokedFalse(Long userId);
}