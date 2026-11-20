package br.com.wedding.api.admin.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class CsrfResponse {

    private final String headerName;
    private final String token;
}
