package br.com.wedding.api.event;

import java.time.Instant;
import java.util.UUID;

import br.com.wedding.api.event.dto.CreateEventRequest;
import br.com.wedding.api.event.dto.EventResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class EventService {

    private final EventRepository events;

    @Transactional
    public EventResponse create(CreateEventRequest request) {
        Event event = Event.builder()
                .id(UUID.randomUUID())
                .title(request.getTitle().strip())
                .eventDate(request.getEventDate())
                .ceremonyLocation(request.getCeremonyLocation().strip())
                .receptionLocation(request.getReceptionLocation().strip())
                .ceremonyMapsUrl(request.getCeremonyMapsUrl())
                .receptionMapsUrl(request.getReceptionMapsUrl())
                .rsvpDeadline(request.getRsvpDeadline())
                .createdAt(Instant.now())
                .build();
        events.saveAndFlush(event);
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
