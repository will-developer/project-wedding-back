package br.com.wedding.api.guest.access;

import java.io.Serializable;
import java.security.Principal;
import java.util.UUID;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GuestIdentity implements Principal, Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID groupId;

    private final UUID eventId;

    @Override
    public String getName() {
        return groupId.toString();
    }
}
