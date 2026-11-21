package br.com.wedding.api.invite;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import br.com.wedding.api.common.exceptions.ApplicationExceptions.EventNotFoundException;
import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import br.com.wedding.api.invite.dto.CreateGuestRequest;
import br.com.wedding.api.invite.dto.CreateInviteGroupRequest;
import br.com.wedding.api.invite.dto.GuestResponse;
import br.com.wedding.api.invite.dto.InviteGroupResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.validation.annotation.Validated;

@Service
@Validated
@RequiredArgsConstructor
public class InviteRegistrationService {

    private final EventRepository events;
    private final InviteGroupRepository groups;
    private final GuestRepository guests;
    private final InviteCodeGenerator codeGenerator;

    @Transactional
    public InviteGroupResponse create(@NotNull UUID eventId, @NotNull @Valid CreateInviteGroupRequest request) {
        Event event = events.findById(eventId).orElseThrow(EventNotFoundException::new);
        InviteGroup group = createGroup(event, request);
        List<Guest> newGuests = request.getGuests().stream()
                .map(input -> toGuest(input, group))
                .toList();
        List<Guest> savedGuests = guests.saveAll(newGuests);
        guests.flush();

        return toResponse(group, savedGuests);
    }

    private InviteGroup createGroup(Event event, CreateInviteGroupRequest request) {
        InviteGroup group = InviteGroup.builder()
                .id(UUID.randomUUID())
                .event(event)
                .groupName(request.getGroupName().strip())
                .inviteCode(generateAvailableCode())
                .notes(request.getNotes())
                .createdAt(Instant.now())
                .build();
        return groups.saveAndFlush(group);
    }

    private String generateAvailableCode() {
        String code;
        do {
            code = codeGenerator.generate();
        } while (groups.existsByInviteCode(code));
        return code;
    }

    private Guest toGuest(CreateGuestRequest input, InviteGroup group) {
        return Guest.builder()
                .id(UUID.randomUUID())
                .group(group)
                .name(input.getName().strip())
                .child(input.getIsChild())
                .rsvpStatus(RsvpStatus.PENDING)
                .build();
    }

    private GuestResponse toGuestResponse(Guest guest) {
        return GuestResponse.builder()
                .id(guest.getId())
                .name(guest.getName())
                .child(guest.isChild())
                .rsvpStatus(guest.getRsvpStatus().getDatabaseValue())
                .respondedAt(guest.getRespondedAt())
                .build();
    }

    private InviteGroupResponse toResponse(InviteGroup group, List<Guest> savedGuests) {
        return InviteGroupResponse.builder()
                .id(group.getId())
                .eventId(group.getEvent().getId())
                .groupName(group.getGroupName())
                .inviteCode(group.getInviteCode())
                .notes(group.getNotes())
                .createdAt(group.getCreatedAt())
                .guests(savedGuests.stream().map(this::toGuestResponse).toList())
                .build();
    }
}
