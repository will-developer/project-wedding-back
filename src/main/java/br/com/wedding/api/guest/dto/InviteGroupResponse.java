package br.com.wedding.api.guest.dto;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class InviteGroupResponse {

    private final UUID id;

    private final UUID eventId;

    private final String groupName;

    private final String inviteCode;

    private final String notes;

    private final Instant createdAt;

    private final List<GuestResponse> guests;
}
