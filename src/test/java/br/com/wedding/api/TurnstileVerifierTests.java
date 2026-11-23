package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;

import br.com.wedding.api.guest.access.GuestAccessProperties;
import br.com.wedding.api.guest.access.TurnstileVerifier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class TurnstileVerifierTests {

    private static final String URL = "https://challenges.cloudflare.com/turnstile/v0/siteverify";
    private GuestAccessProperties properties;
    private MockRestServiceServer server;
    private TurnstileVerifier verifier;

    @BeforeEach
    void prepareClient() {
        properties = new GuestAccessProperties();
        properties.getTurnstile().setSiteKey("public-site-key");
        properties.getTurnstile().setSecretKey("test-secret-not-a-real-key");
        properties.getTurnstile().setExpectedHostname("wedding.example");
        RestClient.Builder builder = RestClient.builder();
        server = MockRestServiceServer.bindTo(builder).build();
        verifier = new TurnstileVerifier(builder.build(), properties);
    }

    @Test
    void acceptsOnlySuccessfulValidationForExpectedHostnameAndAction() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"success\":true,\"hostname\":\"wedding.example\",\"action\":\"guest-login\"}", MediaType.APPLICATION_JSON));
        assertThat(verifier.verify("valid-token")).isTrue();
        server.verify();
    }

    @ParameterizedTest
    @CsvSource({"other.example,guest-login", "wedding.example,other-action"})
    void rejectsUnexpectedHostnameOrAction(String hostname, String action) {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"success\":true,\"hostname\":\"%s\",\"action\":\"%s\"}".formatted(hostname, action), MediaType.APPLICATION_JSON));
        assertThat(verifier.verify("token")).isFalse();
        server.verify();
    }

    @Test
    void rejectsExpiredOrPreviouslyRedeemedToken() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"success\":false,\"error-codes\":[\"timeout-or-duplicate\"]}", MediaType.APPLICATION_JSON));
        assertThat(verifier.verify("spent-token")).isFalse();
        server.verify();
    }

    @Test
    void emptyAndOversizedTokensDoNotCallCloudflare() {
        assertThat(verifier.verify(null)).isFalse();
        assertThat(verifier.verify(" ")).isFalse();
        assertThat(verifier.verify("a".repeat(2049))).isFalse();
        server.verify();
    }

    @Test
    void unavailableCloudflareAndInvalidSecretDoNotApproveTokens() {
        server.expect(requestTo(URL)).andRespond(withServerError());
        assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(AuthenticationServiceException.class);
        server.verify();
        server.reset();
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"success\":false,\"error-codes\":[\"invalid-input-secret\"]}", MediaType.APPLICATION_JSON));
        assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(AuthenticationServiceException.class);
        server.verify();
    }

    @Test
    void missingConfigurationCannotApproveTokens() {
        properties.getTurnstile().setSecretKey("");
        assertThatThrownBy(() -> verifier.verify("token")).isInstanceOf(AuthenticationServiceException.class);
        server.verify();
    }

    @Test
    void rejectsSuccessfulResponseWithoutExpectedAction() {
        server.expect(requestTo(URL)).andRespond(withSuccess(
                "{\"success\":true,\"hostname\":\"wedding.example\"}", MediaType.APPLICATION_JSON));
        assertThat(verifier.verify("token")).isFalse();
        server.verify();
    }
}
