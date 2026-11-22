package br.com.wedding.api.event;

import static org.springframework.util.StringUtils.hasText;

import java.time.Instant;
import java.util.UUID;

import br.com.wedding.api.common.exceptions.ApplicationExceptions.ReceptionDetailsRequiredException;
import br.com.wedding.api.common.exceptions.ApplicationExceptions.EventNotFoundException;
import br.com.wedding.api.event.dto.EventRequest;
import br.com.wedding.api.event.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository events;

    @Transactional
    public EventResponse create(EventRequest request, boolean receptionAtCeremonyLocation) {
        validateReception(request, receptionAtCeremonyLocation);

        Event event = Event.builder()
                .id(UUID.randomUUID())
                .title(request.getTitle().strip())
                .eventDate(request.getEventDate())
                .ceremonyLocation(request.getCeremonyLocation().strip())
                .receptionLocation(receptionAtCeremonyLocation ? null : request.getReceptionLocation().strip())
                .ceremonyMapsUrl(request.getCeremonyMapsUrl())
                .receptionMapsUrl(receptionAtCeremonyLocation ? null : request.getReceptionMapsUrl())
                .rsvpDeadline(request.getRsvpDeadline())
                .createdAt(Instant.now())
                .build();
        events.saveAndFlush(event);
        return toResponse(event);
    }

    @Transactional
    public EventResponse update(UUID eventId, EventRequest request, boolean receptionAtCeremonyLocation) {
        Event event = events.findById(eventId).orElseThrow(EventNotFoundException::new);
        validateReception(request, receptionAtCeremonyLocation);
        event.setTitle(request.getTitle().strip());
        event.setEventDate(request.getEventDate());
        event.setCeremonyLocation(request.getCeremonyLocation().strip());
        event.setCeremonyMapsUrl(request.getCeremonyMapsUrl());
        event.setReceptionLocation(receptionAtCeremonyLocation ? null : request.getReceptionLocation().strip());
        event.setReceptionMapsUrl(receptionAtCeremonyLocation ? null : request.getReceptionMapsUrl());
        event.setRsvpDeadline(request.getRsvpDeadline());
        events.saveAndFlush(event);
        return toResponse(event);
    }

    private void validateReception(EventRequest request, boolean receptionAtCeremonyLocation) {
        if (!receptionAtCeremonyLocation
                && (!hasText(request.getReceptionLocation()) || !hasText(request.getReceptionMapsUrl()))) {
            throw new ReceptionDetailsRequiredException();
        }
    }

    private EventResponse toResponse(Event event) {
        return EventResponse.builder()
                .id(event.getId())
                .title(event.getTitle())
                .eventDate(event.getEventDate())
                .ceremonyLocation(event.getCeremonyLocation())
                .receptionLocation(event.getReceptionLocation())
                .ceremonyMapsUrl(event.getCeremonyMapsUrl())
                .receptionMapsUrl(event.getReceptionMapsUrl())
                .rsvpDeadline(event.getRsvpDeadline())
                .createdAt(event.getCreatedAt())
                .build();
    }
}
