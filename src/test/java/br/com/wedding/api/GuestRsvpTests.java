package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;

import br.com.wedding.api.common.exceptions.ApplicationExceptions.GuestNotFoundException;
import br.com.wedding.api.common.exceptions.ApplicationExceptions.RsvpDeadlinePassedException;
import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import br.com.wedding.api.guest.Guest;
import br.com.wedding.api.guest.GuestRepository;
import br.com.wedding.api.guest.InviteGroup;
import br.com.wedding.api.guest.InviteGroupRepository;
import br.com.wedding.api.guest.RsvpStatus;
import br.com.wedding.api.guest.access.GuestIdentity;
import br.com.wedding.api.guest.dto.GuestResponse;
import br.com.wedding.api.guest.GuestService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@Transactional
class GuestRsvpTests {

    private static final Instant RESPONSE_TIME = Instant.parse("2027-10-23T15:00:00Z");

    @Autowired
    private GuestService service;
    @Autowired
    private EventRepository events;
    @Autowired
    private InviteGroupRepository groups;
    @Autowired
    private GuestRepository guests;
    @Autowired
    private EntityManager entityManager;
    @MockitoBean
    private Clock clock;

    private Guest guest;
    private Guest otherGuest;
    private GuestIdentity identity;

    @BeforeEach
    void prepareInvitations() {
        when(clock.instant()).thenReturn(RESPONSE_TIME);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
        Event event = events.saveAndFlush(Event.builder()
                .id(UUID.randomUUID())
                .title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Venue")
                .receptionLocation("Venue")
                .ceremonyMapsUrl("https://www.google.com/maps")
                .receptionMapsUrl("https://www.google.com/maps")
                .rsvpDeadline(LocalDate.of(2027, 10, 23))
                .createdAt(RESPONSE_TIME.minusSeconds(3600))
                .build());
        InviteGroup family = saveGroup(event, "First family", "A1B2C3");
        InviteGroup otherFamily = saveGroup(event, "Other family", "D4E5F6");
        guest = saveGuest(family, "First guest");
        otherGuest = saveGuest(otherFamily, "Other guest");
        identity = new GuestIdentity(family.getId(), event.getId());
    }

    @ParameterizedTest
    @CsvSource({"true,CONFIRMED,confirmed", "false,DECLINED,declined"})
    void persistsResponseAndServerTimestamp(boolean attending, RsvpStatus status, String responseStatus) {
        GuestResponse response = service.respond(identity, guest.getId(), attending);
        guests.flush();
        entityManager.clear();

        Guest saved = guests.findById(guest.getId()).orElseThrow();
        assertThat(saved.getRsvpStatus()).isEqualTo(status);
        assertThat(saved.getRespondedAt()).isEqualTo(RESPONSE_TIME);
        assertThat(response.getRsvpStatus()).isEqualTo(responseStatus);
        assertThat(response.getRespondedAt()).isEqualTo(RESPONSE_TIME);
        assertThat(guests.findById(otherGuest.getId()).orElseThrow().getRsvpStatus()).isEqualTo(RsvpStatus.PENDING);
    }

    @ParameterizedTest
    @CsvSource({"true,false,DECLINED", "false,true,CONFIRMED"})
    void allowsChangingAnswerBeforeDeadline(boolean firstAnswer, boolean nextAnswer, RsvpStatus expected) {
        service.respond(identity, guest.getId(), firstAnswer);
        Instant changedAt = RESPONSE_TIME.plusSeconds(3600);
        when(clock.instant()).thenReturn(changedAt);

        GuestResponse response = service.respond(identity, guest.getId(), nextAnswer);

        assertThat(response.getRsvpStatus()).isEqualTo(expected.getDatabaseValue());
        assertThat(response.getRespondedAt()).isEqualTo(changedAt);
        assertThat(guests.count()).isEqualTo(2);
    }

    @Test
    void repeatingTheSameAnswerPreservesResponseTime() {
        service.respond(identity, guest.getId(), true);
        when(clock.instant()).thenReturn(RESPONSE_TIME.plusSeconds(3600));

        GuestResponse response = service.respond(identity, guest.getId(), true);

        assertThat(response.getRespondedAt()).isEqualTo(RESPONSE_TIME);
    }

    @Test
    void cannotRespondForAnotherFamily() {
        assertThatThrownBy(() -> service.respond(identity, otherGuest.getId(), true))
                .isInstanceOf(GuestNotFoundException.class);
        assertThat(otherGuest.getRsvpStatus()).isEqualTo(RsvpStatus.PENDING);
        assertThat(otherGuest.getRespondedAt()).isNull();
    }

    @Test
    void rejectsIdentityFromAnotherEvent() {
        GuestIdentity wrongEvent = new GuestIdentity(identity.getGroupId(), UUID.randomUUID());

        assertThatThrownBy(() -> service.respond(wrongEvent, guest.getId(), true))
                .isInstanceOf(GuestNotFoundException.class);
    }

    @Test
    void unknownGuestHasTheSameErrorAsAnotherFamily() {
        assertThatThrownBy(() -> service.respond(identity, UUID.randomUUID(), true))
                .isInstanceOf(GuestNotFoundException.class);
    }

    @Test
    void acceptsLastSecondOfDeadlineDayInSaoPaulo() {
        when(clock.instant()).thenReturn(Instant.parse("2027-10-24T02:59:59Z"));

        assertThat(service.respond(identity, guest.getId(), true).getRsvpStatus()).isEqualTo("confirmed");
    }

    @Test
    void rejectsAnswerAtMidnightAfterDeadlineWithoutChangingGuest() {
        when(clock.instant()).thenReturn(Instant.parse("2027-10-24T03:00:00Z"));

        assertThatThrownBy(() -> service.respond(identity, guest.getId(), true))
                .isInstanceOf(RsvpDeadlinePassedException.class);
        assertThat(guest.getRsvpStatus()).isEqualTo(RsvpStatus.PENDING);
        assertThat(guest.getRespondedAt()).isNull();
    }

    private InviteGroup saveGroup(Event event, String name, String code) {
        return groups.saveAndFlush(InviteGroup.builder()
                .id(UUID.randomUUID()).event(event).groupName(name).inviteCode(code).createdAt(RESPONSE_TIME)
                .build());
    }

    private Guest saveGuest(InviteGroup group, String name) {
        return guests.saveAndFlush(Guest.builder()
                .id(UUID.randomUUID()).group(group).name(name).rsvpStatus(RsvpStatus.PENDING)
                .build());
    }
}
