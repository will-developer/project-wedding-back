package br.com.wedding.api.common.exceptions;

public final class ApplicationExceptions {

    private ApplicationExceptions() {
    }

    public static class EventNotFoundException extends RuntimeException {

        public EventNotFoundException() {
            super("The event was not found.");
        }
    }

}
