package br.com.wedding.api.guest.access;

import java.util.UUID;

import br.com.wedding.api.guest.GuestService;
import br.com.wedding.api.guest.access.dto.GuestInvitationResponse;
import br.com.wedding.api.guest.dto.GuestResponse;
import br.com.wedding.api.guest.dto.UpdateGuestRsvpRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
public class GuestInvitationController {

    private final GuestService guestService;

    @GetMapping("/invitation")
    public ResponseEntity<GuestInvitationResponse> invitation(@AuthenticationPrincipal GuestIdentity identity) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(guestService.invitation(identity));
    }

    @PutMapping("/guests/{guestId}/rsvp")
    public ResponseEntity<GuestResponse> respond(@AuthenticationPrincipal GuestIdentity identity,
            @PathVariable UUID guestId, @Valid @RequestBody UpdateGuestRsvpRequest request) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(guestService.respond(identity, guestId, request.getAttending()));
    }
}
