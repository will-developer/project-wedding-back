package br.com.wedding.api.guest;


import br.com.wedding.api.guest.access.dto.GuestInvitationResponse;
import br.com.wedding.api.guest.access.GuestIdentity;
import br.com.wedding.api.guest.dto.GuestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestService {

    private final GuestRepository guests;
    private final InviteGroupRepository groups;

    @Transactional(readOnly = true)
    public GuestInvitationResponse invitation(GuestIdentity identity) {
        InviteGroup group = groups.findById(identity.getGroupId())
                .filter(invite -> invite.getEvent().getId().equals(identity.getEventId()))
                .orElseThrow(() -> new BadCredentialsException("Invalid invitation session."));

        return GuestInvitationResponse.builder()
                .groupName(group.getGroupName())
                .guests(guests.findAllByGroupIdOrderByNameAscIdAsc(group.getId()).stream()
                        .map(this::toResponse)
                        .toList())
                .build();
    }

    private GuestResponse toResponse(Guest guest) {
        return GuestResponse.builder()
                .id(guest.getId())
                .name(guest.getName())
                .child(guest.isChild())
                .rsvpStatus(guest.getRsvpStatus().getDatabaseValue())
                .respondedAt(guest.getRespondedAt())
                .build();
    }
}
