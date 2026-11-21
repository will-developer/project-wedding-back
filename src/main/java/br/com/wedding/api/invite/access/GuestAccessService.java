package br.com.wedding.api.invite.access;

import static java.util.Locale.ROOT;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;

import br.com.wedding.api.invite.InviteGroup;
import br.com.wedding.api.invite.InviteGroupRepository;
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
