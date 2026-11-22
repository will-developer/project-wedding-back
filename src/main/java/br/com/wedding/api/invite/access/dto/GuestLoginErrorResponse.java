package br.com.wedding.api.invite.access.dto;

import static com.fasterxml.jackson.annotation.JsonInclude.Include.NON_NULL;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(NON_NULL)
public class GuestLoginErrorResponse {

    private final String code;

    private final boolean turnstileRequired;

    private final String siteKey;

    private final String action;
}
