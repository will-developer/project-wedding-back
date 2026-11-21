package br.com.wedding.api.event.dto;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class EventResponse {

    private final UUID id;
    private final String title;
    private final Instant eventDate;
    private final String ceremonyLocation;
    private final String receptionLocation;
    private final String ceremonyMapsUrl;
    private final String receptionMapsUrl;
    private final LocalDate rsvpDeadline;
    private final Instant createdAt;
}
