package br.com.wedding.api.invite.access;

import static java.util.Locale.ROOT;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;

import br.com.wedding.api.invite.InviteGroup;
import br.com.wedding.api.invite.InviteGroupRepository;
import br.com.wedding.api.invite.GuestRepository;
import br.com.wedding.api.invite.access.dto.GuestInvitationResponse;
import br.com.wedding.api.invite.dto.GuestResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class GuestAccessService {

    private final InviteGroupRepository groups;
    private final GuestRepository guests;

    @Transactional(readOnly = true)
    public GuestInvitationResponse invitation(GuestIdentity identity) {
        InviteGroup group = groups.findById(identity.getGroupId())
                .filter(invite -> invite.getEvent().getId().equals(identity.getEventId()))
                .orElseThrow(() -> new BadCredentialsException("Invalid invitation session."));

        return GuestInvitationResponse.builder()
                .groupName(group.getGroupName())
                .guests(guests.findAllByGroupIdOrderByNameAscIdAsc(group.getId()).stream()
                        .map(guest -> GuestResponse.builder()
                                .id(guest.getId())
                                .name(guest.getName())
                                .child(guest.isChild())
                                .rsvpStatus(guest.getRsvpStatus().getDatabaseValue())
                                .respondedAt(guest.getRespondedAt())
                                .build())
                        .toList())
                .build();
    }

    @Transactional(readOnly = true)
    public Authentication authenticate(String rawCode) {
        if (rawCode == null) {
            throw new BadCredentialsException("Invalid invitation code.");
        }

        String code = rawCode.strip().toUpperCase(ROOT);
        if (!code.matches("[A-Z0-9]{6}")) {
            throw new BadCredentialsException("Invalid invitation code.");
        }

        InviteGroup group = groups.findByInviteCode(code)
                .orElseThrow(() -> new BadCredentialsException("Invalid invitation code."));
        GuestIdentity identity = new GuestIdentity(group.getId(), group.getEvent().getId());
        return UsernamePasswordAuthenticationToken.authenticated(identity, null, createAuthorityList("ROLE_GUEST"));
    }
}
