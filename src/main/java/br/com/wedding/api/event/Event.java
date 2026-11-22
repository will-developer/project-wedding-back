package br.com.wedding.api.event;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "event")
@Getter
@Builder
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    @Setter
    private String title;

    @Column(name = "event_date", nullable = false)
    @Setter
    private Instant eventDate;

    @Column(name = "ceremony_location", nullable = false, length = 200)
    @Setter
    private String ceremonyLocation;

    @Column(name = "reception_location", length = 200)
    @Setter
    private String receptionLocation;

    @Column(name = "ceremony_maps_url", nullable = false, length = 2048)
    @Setter
    private String ceremonyMapsUrl;

    @Column(name = "reception_maps_url", length = 2048)
    @Setter
    private String receptionMapsUrl;

    @Column(name = "rsvp_deadline", nullable = false)
    @Setter
    private LocalDate rsvpDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

}
