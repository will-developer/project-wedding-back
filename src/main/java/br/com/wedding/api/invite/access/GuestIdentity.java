package br.com.wedding.api.invite.access;

import java.io.Serializable;
import java.util.UUID;

import lombok.Getter;
import lombok.RequiredArgsConstructor;

@Getter
@RequiredArgsConstructor
public class GuestIdentity implements Serializable {

    private static final long serialVersionUID = 1L;

    private final UUID groupId;

    private final UUID eventId;
}
