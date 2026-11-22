package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.time.Instant;
import java.sql.Timestamp;

import br.com.wedding.api.invite.access.GuestLoginAttemptService;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
class GuestLoginAttemptsTests {

    @Autowired
    private GuestLoginAttemptService service;
    @Autowired
    private JdbcTemplate jdbc;

    @BeforeEach
    @AfterEach
    void cleanAttempts() {
        jdbc.update("DELETE FROM guest_login_attempt");
    }

    @Test
    void concurrentRequestsCannotExceedTheSharedLimit() throws Exception {
        var executor = Executors.newFixedThreadPool(4);
        try {
            List<Future<Boolean>> attempts = new ArrayList<>();
            for (int attempt = 0; attempt < 20; attempt++) {
                attempts.add(executor.submit(() -> service.allow("test-client")));
            }
            int allowed = 0;
            for (Future<Boolean> attempt : attempts) {
                if (attempt.get(10, TimeUnit.SECONDS)) {
                    allowed++;
                }
            }
            assertThat(allowed).isEqualTo(10);
            assertThat(jdbc.queryForObject("SELECT attempts FROM guest_login_attempt WHERE source_address = 'test-client'",
                    Integer.class)).isEqualTo(10);
        } finally {
            executor.shutdownNow();
        }
    }

    @Test
    void blockingOneAddressDoesNotBlockAnother() {
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(service.allow("first-client")).isTrue();
        }
        assertThat(service.allow("first-client")).isFalse();
        assertThat(service.allow("second-client")).isTrue();
    }

    @Test
    void onlyFailedCodesTriggerChallengeAndExpiredWindowClearsIt() {
        assertThat(service.allow("first-client")).isTrue();
        assertThat(service.requiresChallenge("first-client")).isFalse();
        for (int failure = 1; failure <= 3; failure++) {
            service.recordFailure("first-client");
            assertThat(service.requiresChallenge("first-client")).isEqualTo(failure == 3);
        }
        assertThat(service.requiresChallenge("second-client")).isFalse();
        jdbc.update("UPDATE guest_login_attempt SET window_started_at = ? WHERE source_address = 'first-client'",
                Timestamp.from(Instant.now().minusSeconds(901)));
        assertThat(service.requiresChallenge("first-client")).isFalse();
        assertThat(service.allow("first-client")).isTrue();
        assertThat(jdbc.queryForObject("SELECT failed_attempts FROM guest_login_attempt WHERE source_address = 'first-client'",
                Integer.class)).isZero();
    }
}
