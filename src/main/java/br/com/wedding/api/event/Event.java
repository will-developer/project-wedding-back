package br.com.wedding.api.event;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Event {

    @Id
    private UUID id;

    @Column(nullable = false, length = 160)
    private String title;

    @Column(name = "event_date", nullable = false)
    private Instant eventDate;

    @Column(name = "ceremony_location", nullable = false, length = 200)
    private String ceremonyLocation;

    @Column(name = "reception_location", length = 200)
    private String receptionLocation;

    @Column(name = "rsvp_deadline")
    private LocalDate rsvpDeadline;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public Event(String title, Instant eventDate, String ceremonyLocation,
            String receptionLocation, LocalDate rsvpDeadline) {
        this.title = requireNonNull(title, "title is required");
        this.eventDate = requireNonNull(eventDate, "eventDate is required");
        this.ceremonyLocation = requireNonNull(ceremonyLocation, "ceremonyLocation is required");
        this.receptionLocation = receptionLocation;
        this.rsvpDeadline = rsvpDeadline;
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }
}
