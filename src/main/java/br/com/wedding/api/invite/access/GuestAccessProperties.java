package br.com.wedding.api.invite.access;

import java.time.Duration;

import jakarta.validation.constraints.Min;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.time.DurationMin;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Getter
@Setter
@Validated
@ConfigurationProperties(prefix = "app.guest-access")
public class GuestAccessProperties {

    @Min(1)
    private int maxAttempts = 10;

    @Min(1)
    private int challengeAfterFailures = 3;

    @Valid
    @NotNull
    private TurnstileProperties turnstile = new TurnstileProperties();

    @NotNull
    @DurationMin(seconds = 1)
    private Duration attemptWindow = Duration.ofMinutes(15);

    @NotNull
    @DurationMin(seconds = 1)
    private Duration sessionTimeout = Duration.ofMinutes(30);
}
