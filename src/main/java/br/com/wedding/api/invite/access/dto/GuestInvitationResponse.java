package br.com.wedding.api.invite.access.dto;

import java.util.List;

import br.com.wedding.api.invite.dto.GuestResponse;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GuestInvitationResponse {

    private final String groupName;

    private final List<GuestResponse> guests;
}
