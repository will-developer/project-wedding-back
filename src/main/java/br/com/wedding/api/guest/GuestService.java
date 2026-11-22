package br.com.wedding.api.guest;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.wedding.api.common.exceptions.ApplicationExceptions.GuestNotFoundException;
import br.com.wedding.api.common.exceptions.ApplicationExceptions.RsvpDeadlinePassedException;
import br.com.wedding.api.event.Event;
import br.com.wedding.api.guest.access.dto.GuestEventResponse;
import br.com.wedding.api.guest.access.dto.GuestInvitationResponse;
import br.com.wedding.api.guest.access.GuestIdentity;
import br.com.wedding.api.guest.dto.GuestResponse;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class GuestService {

    private final GuestRepository guests;
    private final InviteGroupRepository groups;
    private final Clock clock;

    @Transactional(readOnly = true)
    public GuestInvitationResponse invitation(GuestIdentity identity) {
        InviteGroup group = groups.findById(identity.getGroupId())
                .filter(invite -> invite.getEvent().getId().equals(identity.getEventId()))
                .orElseThrow(() -> new BadCredentialsException("Invalid invitation session."));
        Event event = group.getEvent();

        return GuestInvitationResponse.builder()
                .event(GuestEventResponse.builder()
                        .title(event.getTitle())
                        .eventDate(event.getEventDate())
                        .ceremonyLocation(event.getCeremonyLocation())
                        .receptionLocation(event.getReceptionLocation())
                        .ceremonyMapsUrl(event.getCeremonyMapsUrl())
                        .receptionMapsUrl(event.getReceptionMapsUrl())
                        .rsvpDeadline(event.getRsvpDeadline())
                        .build())
                .groupName(group.getGroupName())
                .guests(guests.findAllByGroupIdOrderByNameAscIdAsc(group.getId()).stream()
                        .map(this::toResponse)
                        .toList())
                .build();
    }

    @Transactional
    public GuestResponse respond(@NotNull GuestIdentity identity, @NotNull UUID guestId, boolean attending) {
        Guest guest = guests.findByIdAndGroupIdAndGroupEventId(guestId, identity.getGroupId(), identity.getEventId())
                .orElseThrow(GuestNotFoundException::new);
        Instant now = clock.instant();
        LocalDate today = LocalDate.ofInstant(now, clock.getZone());
        if (today.isAfter(guest.getGroup().getEvent().getRsvpDeadline())) {
            throw new RsvpDeadlinePassedException();
        }

        RsvpStatus status = attending ? RsvpStatus.CONFIRMED : RsvpStatus.DECLINED;
        if (guest.getRsvpStatus() != status) {
            guest.setRsvpStatus(status);
            guest.setRespondedAt(now);
            guests.save(guest);
        }

        return toResponse(guest);
    }

    public RsvpStatus resolveRsvpStatus(Guest guest) {
        if (guest.getRsvpStatus() == RsvpStatus.PENDING
                && LocalDate.now(clock).isAfter(guest.getGroup().getEvent().getRsvpDeadline())) {
            return RsvpStatus.EXPIRED;
        }
        return guest.getRsvpStatus();
    }

    private GuestResponse toResponse(Guest guest) {
        return GuestResponse.builder()
                .id(guest.getId())
                .name(guest.getName())
                .child(guest.isChild())
                .rsvpStatus(resolveRsvpStatus(guest).getDatabaseValue())
                .respondedAt(guest.getRespondedAt())
                .build();
    }
}
