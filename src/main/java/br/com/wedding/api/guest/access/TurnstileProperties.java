package br.com.wedding.api.guest.access;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class TurnstileProperties {

    private String siteKey = "";

    private String secretKey = "";

    private String expectedHostname = "";

    @NotBlank
    private String action = "guest-login";

}
