package br.com.wedding.api.guest.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateGuestRequest {

    @NotBlank
    @Size(max = 160)
    private String name;

    @NotNull
    private Boolean isChild;
}
