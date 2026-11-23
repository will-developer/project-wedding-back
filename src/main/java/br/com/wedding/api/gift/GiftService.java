package br.com.wedding.api.gift;

import static org.springframework.util.StringUtils.hasText;

import java.util.UUID;
import java.util.List;

import br.com.wedding.api.common.exceptions.ApplicationExceptions.EventNotFoundException;
import br.com.wedding.api.common.exceptions.ApplicationExceptions.GiftNotFoundException;
import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import br.com.wedding.api.gift.dto.CreateGiftRequest;
import br.com.wedding.api.gift.dto.GiftResponse;
import br.com.wedding.api.gift.dto.GuestGiftResponse;
import br.com.wedding.api.gift.dto.UpdateGiftRequest;
import br.com.wedding.api.guest.InviteGroupRepository;
import br.com.wedding.api.guest.access.GuestIdentity;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GiftService {

    private final GiftRepository gifts;
    private final EventRepository events;
    private final InviteGroupRepository groups;

    @Transactional(readOnly = true)
    public List<GuestGiftResponse> listForGuest(GuestIdentity identity) {
        if (!groups.existsByIdAndEventId(identity.getGroupId(), identity.getEventId())) {
            throw new BadCredentialsException("Invalid invitation session.");
        }
        return gifts.findAllByEventIdAndActiveTrueOrderByNameAscIdAsc(identity.getEventId()).stream()
                .map(gift -> GuestGiftResponse.builder()
                        .id(gift.getId())
                        .name(gift.getName())
                        .description(gift.getDescription())
                        .imageUrl(gift.getImageUrl())
                        .price(gift.getPrice())
                        .availableQuantity(gift.getAvailableQuantity())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<GiftResponse> list(UUID eventId) {
        if (!events.existsById(eventId)) {
            throw new EventNotFoundException();
        }
        return gifts.findAllByEventIdOrderByNameAscIdAsc(eventId).stream()
                .map(this::toResponse)
                .toList();
    }

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
        return toResponse(gift);
    }

    @Transactional
    public GiftResponse update(UUID eventId, UUID giftId, UpdateGiftRequest request) {
        Gift gift = gifts.findByIdAndEventId(giftId, eventId).orElseThrow(GiftNotFoundException::new);
        gift.setName(request.getName().strip());
        gift.setDescription(request.getDescription());
        gift.setImageUrl(hasText(request.getImageUrl()) ? request.getImageUrl() : null);
        gift.setPrice(request.getPrice());
        gifts.saveAndFlush(gift);
        return toResponse(gift);
    }

    @Transactional
    public GiftResponse setActive(UUID eventId, UUID giftId, boolean active) {
        Gift gift = gifts.findByIdAndEventId(giftId, eventId).orElseThrow(GiftNotFoundException::new);
        gift.setActive(active);
        gifts.saveAndFlush(gift);
        return toResponse(gift);
    }

    private GiftResponse toResponse(Gift gift) {
        return GiftResponse.builder()
                .id(gift.getId())
                .eventId(gift.getEvent().getId())
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
