package com.thesis.elearning.modules.auth.service;

import static org.assertj.core.api.Assertions.assertThat;

import com.thesis.elearning.modules.auth.dto.RefreshTokenRequest;
import com.thesis.elearning.modules.auth.repository.RefreshTokenRepository;
import com.thesis.elearning.modules.auth.service.AuthService;
import jakarta.persistence.LockModeType;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

/**
 * Proves refresh-token rotation is atomic, against a real database.
 *
 * <h2>Why this is an integration test and not a unit test</h2>
 * The defect being guarded against is a database-level race: two transactions
 * both read {@code revoked = false} and both write. A mocked repository cannot
 * express it, because the mock answers from a single-threaded map and would hand
 * both callers the same object. The race only exists between two real
 * connections, so the test needs real transactions and a real lock.
 *
 * <h2>What the assertion actually proves</h2>
 * Without {@code PESSIMISTIC_WRITE} both threads succeed and this test fails,
 * which is the point: it is a regression guard, not a description of current
 * behaviour. The lock is additionally asserted reflectively, so the guarantee
 * does not silently disappear if someone swaps the method back.
 *
 * <p>Both callers are released from a {@link CountDownLatch} at the same instant
 * to maximise the overlap window; without the latch they would very likely
 * serialise by accident and the test would pass vacuously.
 */
@SpringBootTest
@ActiveProfiles("test")
class AuthRefreshConcurrencyTest {

    private static final long USER_ID = 900_001L;
    private static final String USERNAME = "race_tester";
    private static final String RAW_TOKEN = "raw-refresh-token-for-race";

    @Autowired private AuthService authService;
    @Autowired private com.thesis.elearning.config.security.JwtTokenProvider tokenProvider;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private RefreshTokenRepository refreshTokenRepository;

    /**
     * Inserts the user and token rows directly.
     *
     * <p>{@code users.created_at} and {@code refresh_tokens.created_at} are mapped
     * {@code insertable = false} and carry no column default in the generated H2
     * schema, so a repository save would omit a NOT NULL column. Raw inserts keep
     * the fixture honest about what the database actually requires.
     */
    private void seed() {
        // The service looks the token up by its SHA-256 digest, never by the raw value,
        // so the fixture must store the digest too.
        // Every token this user holds, not just the one under test: an earlier
        // test in this class issues real session tokens, and deleting the user
        // while those rows reference it would trip the foreign key.
        jdbc.update("DELETE FROM refresh_tokens WHERE user_id = ?", USER_ID);
        final String digest = tokenProvider.hashRefreshToken(RAW_TOKEN);

        jdbc.update("DELETE FROM users WHERE user_id = ?", USER_ID);
        jdbc.update(
                "INSERT INTO users (user_id, username, email, password_hash, full_name,"
                        + " is_active, is_deleted, created_at, updated_at)"
                        + " VALUES (?,?,?,?,?,1,0,CURRENT_TIMESTAMP,CURRENT_TIMESTAMP)",
                USER_ID, USERNAME, "race@test.local", "{noop}", "Race Tester");
        jdbc.update(
                "INSERT INTO refresh_tokens (user_id, token, expiry_date, revoked, created_at)"
                        + " VALUES (?,?,?,0,CURRENT_TIMESTAMP)",
                USER_ID, digest, Timestamp.valueOf(LocalDateTime.now().plusDays(1)));
    }

    @Test
    @DisplayName("the rotation lookup is declared PESSIMISTIC_WRITE")
    void rotationLookupIsLocked() throws NoSuchMethodException {
        final var method = RefreshTokenRepository.class.getMethod("findByTokenForUpdate", String.class);
        final var lock = method.getAnnotation(org.springframework.data.jpa.repository.Lock.class);

        assertThat(lock).isNotNull();
        assertThat(lock.value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    @DisplayName("two concurrent refreshes of one token yield exactly one session")
    void concurrentRefreshIssuesOneSession() throws Exception {
        seed();

        final int threads = 2;
        final CountDownLatch ready = new CountDownLatch(threads);
        final CountDownLatch go = new CountDownLatch(1);
        final AtomicInteger succeeded = new AtomicInteger();
        final AtomicInteger rejected = new AtomicInteger();

        final ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            final List<Future<?>> futures = new ArrayList<>();
            for (int i = 0; i < threads; i++) {
                futures.add(
                        pool.submit(
                                () -> {
                                    ready.countDown();
                                    try {
                                        // Both callers leave the gate together.
                                        go.await(5, TimeUnit.SECONDS);
                                        authService.refresh(new RefreshTokenRequest(RAW_TOKEN));
                                        succeeded.incrementAndGet();
                                    } catch (final InterruptedException ex) {
                                        Thread.currentThread().interrupt();
                                        rejected.incrementAndGet();
                                    } catch (final RuntimeException ex) {
                                        // The loser is expected to be told the token
                                        // is no longer usable.
                                        rejected.incrementAndGet();
                                    }
                                }));
            }

            assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
            go.countDown();

            for (final Future<?> future : futures) {
                future.get(30, TimeUnit.SECONDS);
            }
        } finally {
            pool.shutdownNow();
        }

        assertThat(succeeded.get())
                .as("rotation must mint exactly one session for a single token")
                .isEqualTo(1);
        assertThat(rejected.get())
                .as("the second caller must be refused, not issued a duplicate session")
                .isEqualTo(threads - 1);
    }

    @Test
    @DisplayName("a token cannot be exchanged twice, even sequentially")
    void sequentialReplayIsRefused() {
        seed();

        authService.refresh(new RefreshTokenRequest(RAW_TOKEN));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                        () -> authService.refresh(new RefreshTokenRequest(RAW_TOKEN)))
                .isInstanceOf(com.thesis.elearning.common.exception.AppException.class);
    }
}