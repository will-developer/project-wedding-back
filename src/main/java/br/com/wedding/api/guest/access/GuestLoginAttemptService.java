package br.com.wedding.api.guest.access;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestLoginAttemptService {

    private final GuestLoginAttemptRepository attempts;
    private final GuestAccessProperties properties;

    @Retryable(includes = {OptimisticLockingFailureException.class, DataIntegrityViolationException.class},
            maxRetries = 5, delay = 20)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean allow(String address) {
        Instant now = Instant.now();
        GuestLoginAttempt attempt = attempts.findById(address).orElseGet(() -> GuestLoginAttempt.builder()
                .sourceAddress(address).windowStartedAt(now).attempts(0).build());

        if (!now.isBefore(attempt.getWindowStartedAt().plus(properties.getAttemptWindow()))) {
            attempt.setWindowStartedAt(now);
            attempt.setAttempts(0);
            attempt.setFailedAttempts(0);
        }
        if (attempt.getAttempts() >= properties.getMaxAttempts()) {
            return false;
        }

        attempt.setAttempts(attempt.getAttempts() + 1);
        attempts.saveAndFlush(attempt);
        return true;
    }

    @Transactional(readOnly = true)
    public boolean requiresChallenge(String address) {
        Instant now = Instant.now();
        return attempts.findById(address)
                .filter(attempt -> now.isBefore(attempt.getWindowStartedAt().plus(properties.getAttemptWindow())))
                .map(attempt -> attempt.getFailedAttempts() >= properties.getChallengeAfterFailures())
                .orElse(false);
    }

    @Retryable(includes = OptimisticLockingFailureException.class, maxRetries = 5, delay = 20)
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordFailure(String address) {
        Instant now = Instant.now();
        attempts.findById(address)
                .filter(attempt -> now.isBefore(attempt.getWindowStartedAt().plus(properties.getAttemptWindow())))
                .ifPresent(attempt -> {
                    attempt.setFailedAttempts(attempt.getFailedAttempts() + 1);
                    attempts.saveAndFlush(attempt);
                });
    }
}
