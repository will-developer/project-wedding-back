package br.com.wedding.api.guest.access.dto;

import java.time.Instant;
import java.time.LocalDate;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GuestEventResponse {

    private final String title;

    private final Instant eventDate;

    private final String ceremonyLocation;

    private final String receptionLocation;

    private final String ceremonyMapsUrl;

    private final String receptionMapsUrl;

    private final LocalDate rsvpDeadline;
}
