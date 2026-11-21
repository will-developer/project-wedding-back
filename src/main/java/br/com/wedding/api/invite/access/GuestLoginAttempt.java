package br.com.wedding.api.invite.access;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "guest_login_attempt")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class GuestLoginAttempt {

    @Id
    @Column(name = "source_address", length = 64)
    private String sourceAddress;

    @Setter
    @Column(name = "window_started_at", nullable = false)
    private Instant windowStartedAt;

    @Setter
    @Column(nullable = false)
    private int attempts;

    @Version
    @Column(nullable = false)
    private Long version;
}
