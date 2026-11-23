package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import br.com.wedding.api.guest.Guest;
import br.com.wedding.api.guest.GuestRepository;
import br.com.wedding.api.guest.InviteGroup;
import br.com.wedding.api.guest.InviteGroupRepository;
import br.com.wedding.api.guest.RsvpStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@Transactional
class InvitePersistenceTests {

    @Autowired
    private EventRepository events;
    @Autowired
    private InviteGroupRepository groups;
    @Autowired
    private GuestRepository guests;
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsEventGroupAndGuestsUsingExistingSchemaAndNativeEnum() {
        Event event = events.saveAndFlush(Event.builder()
                .id(UUID.randomUUID())
                .title("Test wedding")
                .eventDate(Instant.parse("2027-06-12T18:00:00Z"))
                .ceremonyLocation("Ceremony")
                .receptionLocation("Reception")
                .ceremonyMapsUrl("https://maps.app.goo.gl/testCeremony")
                .receptionMapsUrl("https://maps.app.goo.gl/testReception")
                .rsvpDeadline(LocalDate.of(2027, 6, 1))
                .createdAt(Instant.now())
                .build());
        InviteGroup group = groups.saveAndFlush(InviteGroup.builder()
                .id(UUID.randomUUID())
                .event(event)
                .groupName("Test family")
                .inviteCode("A1B2C3")
                .createdAt(Instant.now())
                .build());
        Guest adult = guests.saveAndFlush(Guest.builder()
                .id(UUID.randomUUID())
                .group(group)
                .name("Test adult")
                .child(false)
                .rsvpStatus(RsvpStatus.PENDING)
                .build());
        Guest child = guests.saveAndFlush(Guest.builder()
                .id(UUID.randomUUID())
                .group(group)
                .name("Test child")
                .child(true)
                .rsvpStatus(RsvpStatus.PENDING)
                .build());
        entityManager.clear();

        Guest loaded = guests.findById(adult.getId()).orElseThrow();
        assertThat(loaded.getGroup().getId()).isEqualTo(group.getId());
        assertThat(loaded.getGroup().getEvent().getId()).isEqualTo(event.getId());
        assertThat(loaded.getGroup().getEvent().getRsvpDeadline()).isEqualTo(LocalDate.of(2027, 6, 1));
        assertThat(loaded.getGroup().getEvent().getEventDate()).isEqualTo(event.getEventDate());
        assertThat(loaded.getRsvpStatus()).isEqualTo(RsvpStatus.PENDING);
        assertThat(loaded.getRespondedAt()).isNull();
        assertThat(loaded.isChild()).isFalse();
        assertThat(guests.findById(child.getId()).orElseThrow().isChild()).isTrue();
        assertThat(jdbc.queryForObject("SELECT rsvp_status::text FROM guest WHERE id = ?",
                String.class, adult.getId())).isEqualTo("pending");

        // Verify every existing database label can also be read as its Java enum.
        jdbc.update("UPDATE guest SET rsvp_status = 'confirmed' WHERE id = ?", adult.getId());
        jdbc.update("UPDATE guest SET rsvp_status = 'declined' WHERE id = ?", child.getId());
        entityManager.clear();
        assertThat(guests.findById(adult.getId()).orElseThrow().getRsvpStatus()).isEqualTo(RsvpStatus.CONFIRMED);
        assertThat(guests.findById(child.getId()).orElseThrow().getRsvpStatus()).isEqualTo(RsvpStatus.DECLINED);

        Guest expired = guests.findById(child.getId()).orElseThrow();
        expired.setRsvpStatus(RsvpStatus.EXPIRED);
        guests.saveAndFlush(expired);
        entityManager.clear();
        assertThat(jdbc.queryForObject("SELECT rsvp_status::text FROM guest WHERE id = ?",
                String.class, child.getId())).isEqualTo("expired");
        assertThat(guests.findById(child.getId()).orElseThrow().getRsvpStatus()).isEqualTo(RsvpStatus.EXPIRED);
    }
}
