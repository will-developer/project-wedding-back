package br.com.wedding.api.invite.access;

import static java.lang.Math.toIntExact;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.io.IOException;

import br.com.wedding.api.common.exceptions.ApplicationExceptions.GuestLoginLimitException;
import br.com.wedding.api.common.exceptions.ApplicationExceptions.GuestLoginPayloadException;
import br.com.wedding.api.common.exceptions.ApplicationExceptions.GuestTurnstileRequiredException;
import br.com.wedding.api.invite.access.dto.GuestLoginErrorResponse;
import br.com.wedding.api.invite.access.dto.GuestLoginRequest;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NullMarked;
import org.springframework.dao.DataAccessException;
import org.springframework.http.HttpMethod;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@NullMarked
public class GuestLoginFilter extends AbstractAuthenticationProcessingFilter {

    private static final int MAX_BODY_BYTES = 4096;

    private final GuestLoginAttemptService attempts;
    private final GuestAccessProperties properties;
    private final JsonMapper json;
    private final TurnstileVerifier turnstile;

    public GuestLoginFilter(AuthenticationManager manager, GuestLoginAttemptService attempts,
            GuestAccessProperties properties, JsonMapper json, TurnstileVerifier turnstile) {
        super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/guest/login"), manager);
        this.attempts = attempts;
        this.properties = properties;
        this.json = json;
        this.turnstile = turnstile;
        setAuthenticationSuccessHandler(this::onSuccess);
        setAuthenticationFailureHandler(this::onFailure);
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try {
            if (!attempts.allow(request.getRemoteAddr())) {
                throw new GuestLoginLimitException();
            }

            String contentType = request.getContentType();
            if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) {
                throw new GuestLoginPayloadException();
            }
            byte[] bytes = request.getInputStream().readNBytes(MAX_BODY_BYTES + 1);
            if (bytes.length > MAX_BODY_BYTES) {
                throw new GuestLoginPayloadException();
            }
            GuestLoginRequest body = json.readValue(bytes, GuestLoginRequest.class);
            if (body == null) {
                throw new GuestLoginPayloadException();
            }
            if (attempts.requiresChallenge(request.getRemoteAddr()) && !turnstile.verify(body.getTurnstileToken())) {
                throw new GuestTurnstileRequiredException();
            }
            return getAuthenticationManager().authenticate(
                    UsernamePasswordAuthenticationToken.unauthenticated("guest", body.getCode()));
        } catch (JacksonException | InvalidMediaTypeException exception) {
            throw new GuestLoginPayloadException();
        } catch (DataAccessException exception) {
            throw new AuthenticationServiceException("Invitation login is temporarily unavailable.", exception);
        }
    }

    private void onSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) {
        request.getSession().setMaxInactiveInterval(toIntExact(properties.getSessionTimeout().toSeconds()));
        response.setHeader(CACHE_CONTROL, "no-store");
        response.setStatus(HttpServletResponse.SC_NO_CONTENT);
    }

    private void onFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        int status = HttpServletResponse.SC_UNAUTHORIZED;
        String code = "INVALID_INVITATION_CODE";
        boolean challengeRequired = false;
        try {
            if (exception instanceof BadCredentialsException) {
                attempts.recordFailure(request.getRemoteAddr());
                challengeRequired = attempts.requiresChallenge(request.getRemoteAddr());
            } else if (exception instanceof GuestTurnstileRequiredException) {
                status = HttpServletResponse.SC_FORBIDDEN;
                code = "TURNSTILE_REQUIRED";
                challengeRequired = true;
            } else if (exception instanceof GuestLoginLimitException) {
                status = TOO_MANY_REQUESTS.value();
                code = "TOO_MANY_ATTEMPTS";
                response.setHeader(RETRY_AFTER, Long.toString(properties.getAttemptWindow().toSeconds()));
            } else if (exception instanceof GuestLoginPayloadException) {
                status = HttpServletResponse.SC_BAD_REQUEST;
                code = "INVALID_LOGIN_PAYLOAD";
            } else if (exception instanceof AuthenticationServiceException) {
                status = HttpServletResponse.SC_SERVICE_UNAVAILABLE;
                code = "LOGIN_UNAVAILABLE";
            }
            if (challengeRequired && !turnstile.isConfigured()) {
                writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "TURNSTILE_UNAVAILABLE", false);
                return;
            }
            writeError(response, status, code, challengeRequired);
        } catch (DataAccessException unavailable) {
            writeError(response, HttpServletResponse.SC_SERVICE_UNAVAILABLE, "LOGIN_UNAVAILABLE", false);
        }
    }

    private void writeError(HttpServletResponse response, int status, String code, boolean challengeRequired)
            throws IOException {
        GuestLoginErrorResponse body = GuestLoginErrorResponse.builder()
                .code(code).turnstileRequired(challengeRequired)
                .siteKey(challengeRequired ? properties.getTurnstile().getSiteKey() : null)
                .action(challengeRequired ? properties.getTurnstile().getAction() : null)
                .build();
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader(CACHE_CONTROL, "no-store");
        response.getOutputStream().write(json.writeValueAsBytes(body));
    }
}
