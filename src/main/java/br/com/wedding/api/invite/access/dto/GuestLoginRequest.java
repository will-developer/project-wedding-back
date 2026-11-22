package br.com.wedding.api.invite.access.dto;

import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class GuestLoginRequest {

    private String code;

    private String turnstileToken;
}
