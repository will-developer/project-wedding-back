package br.com.wedding.api.invite.dto;

import java.time.Instant;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class GuestResponse {

    private final UUID id;

    private final String name;

    @JsonProperty("isChild")
    private final boolean child;

    private final String rsvpStatus;

    private final Instant respondedAt;
}
