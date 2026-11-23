package br.com.wedding.api.gift;

import java.util.List;

import br.com.wedding.api.gift.dto.GuestGiftResponse;
import br.com.wedding.api.guest.access.GuestIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/guest/gifts")
@RequiredArgsConstructor
public class GuestGiftController {

    private final GiftService giftService;

    @GetMapping
    public ResponseEntity<List<GuestGiftResponse>> list(@AuthenticationPrincipal GuestIdentity identity) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(giftService.listForGuest(identity));
    }
}
