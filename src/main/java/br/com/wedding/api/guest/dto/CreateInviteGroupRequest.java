package br.com.wedding.api.guest.dto;

import java.util.List;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class CreateInviteGroupRequest {

    @NotBlank
    @Size(max = 160)
    private String groupName;

    private String notes;

    @NotEmpty
    private List<@NotNull @Valid CreateGuestRequest> guests;
}
