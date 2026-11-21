package br.com.wedding.api.invite.access;

import static java.lang.Math.toIntExact;
import static org.springframework.http.HttpHeaders.CACHE_CONTROL;
import static org.springframework.http.HttpHeaders.RETRY_AFTER;
import static org.springframework.http.HttpStatus.TOO_MANY_REQUESTS;

import java.io.IOException;

import br.com.wedding.api.common.exceptions.ApplicationExceptions.GuestLoginLimitException;
import br.com.wedding.api.common.exceptions.ApplicationExceptions.GuestLoginPayloadException;
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
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.AbstractAuthenticationProcessingFilter;
import org.springframework.security.web.servlet.util.matcher.PathPatternRequestMatcher;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.json.JsonMapper;

@NullMarked
public class GuestLoginFilter extends AbstractAuthenticationProcessingFilter {

    private static final int MAX_BODY_BYTES = 512;

    private final GuestLoginAttemptService attempts;
    private final GuestAccessProperties properties;
    private final JsonMapper json;

    public GuestLoginFilter(AuthenticationManager manager, GuestLoginAttemptService attempts,
            GuestAccessProperties properties, JsonMapper json) {
        super(PathPatternRequestMatcher.withDefaults().matcher(HttpMethod.POST, "/api/guest/login"), manager);
        this.attempts = attempts;
        this.properties = properties;
        this.json = json;
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

    private void onFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception) {
        response.setHeader(CACHE_CONTROL, "no-store");
        if (exception instanceof GuestLoginLimitException) {
            response.setHeader(RETRY_AFTER, Long.toString(properties.getAttemptWindow().toSeconds()));
            response.setStatus(TOO_MANY_REQUESTS.value());
        } else if (exception instanceof GuestLoginPayloadException) {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
        } else if (exception instanceof AuthenticationServiceException) {
            response.setStatus(HttpServletResponse.SC_SERVICE_UNAVAILABLE);
        } else {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        }
    }
}
