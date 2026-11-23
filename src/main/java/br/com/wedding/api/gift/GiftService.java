package br.com.wedding.api.gift;

import static org.springframework.util.StringUtils.hasText;

import java.util.UUID;

import br.com.wedding.api.common.exceptions.ApplicationExceptions.EventNotFoundException;
import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import br.com.wedding.api.gift.dto.CreateGiftRequest;
import br.com.wedding.api.gift.dto.GiftResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GiftService {

    private final GiftRepository gifts;
    private final EventRepository events;

    @Transactional
    public GiftResponse create(UUID eventId, CreateGiftRequest request) {
        Event event = events.findById(eventId).orElseThrow(EventNotFoundException::new);
        Gift gift = Gift.builder()
                .id(UUID.randomUUID())
                .event(event)
                .name(request.getName().strip())
                .description(request.getDescription())
                .imageUrl(hasText(request.getImageUrl()) ? request.getImageUrl() : null)
                .price(request.getPrice())
                .totalQuantity(request.getTotalQuantity())
                .availableQuantity(request.getTotalQuantity())
                .active(true)
                .build();
        gifts.saveAndFlush(gift);

        return GiftResponse.builder()
                .id(gift.getId())
                .eventId(event.getId())
                .name(gift.getName())
                .description(gift.getDescription())
                .imageUrl(gift.getImageUrl())
                .price(gift.getPrice())
                .totalQuantity(gift.getTotalQuantity())
                .availableQuantity(gift.getAvailableQuantity())
                .active(gift.isActive())
                .build();
    }
}
