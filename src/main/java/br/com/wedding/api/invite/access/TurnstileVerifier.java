package br.com.wedding.api.invite.access;

import java.util.Map;
import java.util.Set;

import br.com.wedding.api.invite.access.dto.TurnstileVerificationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

@Service
@RequiredArgsConstructor
public class TurnstileVerifier {

    private static final Set<String> SERVICE_ERRORS = Set.of(
            "missing-input-secret", "invalid-input-secret", "internal-error");

    private final RestClient turnstileClient;
    private final GuestAccessProperties properties;

    public boolean isConfigured() {
        TurnstileProperties settings = properties.getTurnstile();
        return !settings.getSiteKey().isBlank() && !settings.getSecretKey().isBlank()
                && !settings.getExpectedHostname().isBlank();
    }

    public boolean verify(String token) {
        if (!isConfigured()) {
            throw new AuthenticationServiceException("Turnstile is not configured.");
        }
        if (token == null || token.isBlank() || token.length() > 2048) {
            return false;
        }

        TurnstileProperties settings = properties.getTurnstile();
        try {
            TurnstileVerificationResponse result = turnstileClient.post()
                    .uri("https://challenges.cloudflare.com/turnstile/v0/siteverify")
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(Map.of("secret", settings.getSecretKey(), "response", token))
                    .retrieve().body(TurnstileVerificationResponse.class);
            if (result != null && result.getErrorCodes() != null
                    && result.getErrorCodes().stream().anyMatch(SERVICE_ERRORS::contains)) {
                throw new AuthenticationServiceException("Turnstile verification is unavailable.");
            }
            return result != null && result.isSuccess()
                    && settings.getExpectedHostname().equals(result.getHostname())
                    && settings.getAction().equals(result.getAction());
        } catch (RestClientException exception) {
            throw new AuthenticationServiceException("Turnstile verification is unavailable.", exception);
        }
    }
}
