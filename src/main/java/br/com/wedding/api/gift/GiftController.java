package br.com.wedding.api.gift;

import java.util.UUID;
import java.util.List;

import br.com.wedding.api.gift.dto.CreateGiftRequest;
import br.com.wedding.api.gift.dto.GiftResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/events/{eventId}/gifts")
@RequiredArgsConstructor
public class GiftController {

    private final GiftService giftService;

    @GetMapping
    public ResponseEntity<List<GiftResponse>> list(@PathVariable UUID eventId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(giftService.list(eventId));
    }

    @PostMapping
    public ResponseEntity<GiftResponse> create(@PathVariable UUID eventId,
            @Valid @RequestBody CreateGiftRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(giftService.create(eventId, request));
    }
}
