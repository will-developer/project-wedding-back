package br.com.wedding.api.guest.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UpdateGuestRsvpRequest {

    @NotNull
    private Boolean attending;
}
