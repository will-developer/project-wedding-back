package br.com.wedding.api.invite.access;

import br.com.wedding.api.invite.access.dto.GuestInvitationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guest")
@RequiredArgsConstructor
public class GuestInvitationController {

    private final GuestAccessService guestAccessService;

    @GetMapping("/invitation")
    public ResponseEntity<GuestInvitationResponse> invitation(@AuthenticationPrincipal GuestIdentity identity) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(guestAccessService.invitation(identity));
    }
}
