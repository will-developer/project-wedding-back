package br.com.wedding.api.event;

import java.util.UUID;
import java.util.List;

import br.com.wedding.api.event.dto.EventRequest;
import br.com.wedding.api.event.dto.EventResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/events")
@RequiredArgsConstructor
public class EventController {

    private final EventService eventService;

    @GetMapping
    public ResponseEntity<List<EventResponse>> list() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(eventService.list());
    }

    @PostMapping
    public ResponseEntity<EventResponse> create(@Valid @RequestBody EventRequest request,
            @RequestParam boolean receptionAtCeremonyLocation) {
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(eventService.create(request, receptionAtCeremonyLocation));
    }

    @PutMapping("/{eventId}")
    public ResponseEntity<EventResponse> update(@PathVariable UUID eventId,
            @Valid @RequestBody EventRequest request, @RequestParam boolean receptionAtCeremonyLocation) {
        return ResponseEntity.ok().body(eventService.update(eventId, request, receptionAtCeremonyLocation));
    }
}
