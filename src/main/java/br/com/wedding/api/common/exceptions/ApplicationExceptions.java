package br.com.wedding.api.common.exceptions;

import org.springframework.security.core.AuthenticationException;

public final class ApplicationExceptions {

    private ApplicationExceptions() {
    }

    public static class EventNotFoundException extends RuntimeException {

        public EventNotFoundException() {
            super("The event was not found.");
        }
    }

    public static class GuestNotFoundException extends RuntimeException {

        public GuestNotFoundException() {
            super("The guest was not found in this invitation.");
        }
    }

    public static class RsvpDeadlinePassedException extends RuntimeException {

        public RsvpDeadlinePassedException() {
            super("The RSVP deadline has passed.");
        }
    }

    public static class GuestLoginLimitException extends AuthenticationException {

        public GuestLoginLimitException() {
            super("Too many invitation login attempts.");
        }
    }

    public static class GuestLoginPayloadException extends AuthenticationException {

        public GuestLoginPayloadException() {
            super("Invalid invitation login payload.");
        }
    }

    public static class GuestTurnstileRequiredException extends AuthenticationException {

        public GuestTurnstileRequiredException() {
            super("Turnstile verification is required.");
        }
    }

}
