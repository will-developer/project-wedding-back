package br.com.wedding.api.event;

import br.com.wedding.api.event.dto.CreateEventRequest;
import br.com.wedding.api.event.dto.EventResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody CreateEventRequest request,
            @RequestParam boolean receptionAtCeremonyLocation) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.create(request, receptionAtCeremonyLocation));
    }
}
