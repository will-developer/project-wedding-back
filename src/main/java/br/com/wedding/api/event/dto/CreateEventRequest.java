package br.com.wedding.api.event.dto;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;
import org.hibernate.validator.constraints.URL;

@Getter
@Setter
public class CreateEventRequest {

    @NotBlank
    @Size(max = 160)
    private String title;

    @NotNull
    private Instant eventDate;

    @NotBlank
    @Size(max = 200)
    private String ceremonyLocation;

    @NotBlank
    @Size(max = 200)
    private String receptionLocation;

    @NotBlank
    @Size(max = 2048)
    @URL(protocol = "https")
    private String ceremonyMapsUrl;

    @NotBlank
    @Size(max = 2048)
    @URL(protocol = "https")
    private String receptionMapsUrl;

    @NotNull
    private LocalDate rsvpDeadline;
}
