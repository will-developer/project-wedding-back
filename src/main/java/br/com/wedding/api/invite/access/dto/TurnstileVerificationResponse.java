package br.com.wedding.api.invite.access.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurnstileVerificationResponse {

    private boolean success;

    private String hostname;

    private String action;

    @JsonProperty("error-codes")
    private List<String> errorCodes = List.of();
}
