package br.com.wedding.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class SessionResponse {

    private final String login;
    private final String role;
}
