package br.com.wedding.api.admin;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.UUID;

import br.com.wedding.api.common.ActivationStatus;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "admin_account")
public class AdminAccount {

    @Id
    private UUID id;

    @Column(nullable = false, unique = true, length = 160)
    private String login;

    @Column(name = "password_hash", nullable = false, length = 255)
    private String passwordHash;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private ActivationStatus status;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    public AdminAccount(String login, String passwordHash, Instant createdAt) {
        this.login = requireText(login, 160, "login");
        this.passwordHash = requireText(passwordHash, 255, "passwordHash");
        this.createdAt = requireNonNull(createdAt, "createdAt is required");
        this.id = UUID.randomUUID();
        this.status = ActivationStatus.ACTIVE;
    }

    public void deactivate(Instant deactivationTime) {
        requireNonNull(deactivationTime, "deactivation time is required");
        if (deactivationTime.isBefore(createdAt)) {
            throw new IllegalArgumentException("deactivation cannot precede account creation");
        }
        if (status == ActivationStatus.INACTIVE) {
            return;
        }
        status = ActivationStatus.INACTIVE;
        deactivatedAt = deactivationTime;
    }

    public void reactivate() {
        status = ActivationStatus.ACTIVE;
        deactivatedAt = null;
    }

    private static String requireText(String value, int maxLength, String field) {
        if (value == null || value.isBlank() || value.length() > maxLength) {
            throw new IllegalArgumentException(field + " must contain between 1 and " + maxLength + " characters");
        }
        return value;
    }

}
