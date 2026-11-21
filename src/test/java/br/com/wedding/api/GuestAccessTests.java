package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import br.com.wedding.api.invite.InviteGroup;
import br.com.wedding.api.invite.InviteGroupRepository;
import br.com.wedding.api.invite.access.GuestAccessService;
import br.com.wedding.api.invite.access.GuestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@Transactional
class GuestAccessTests {

    @Autowired
    private GuestAccessService service;
    @Autowired
    private EventRepository events;
    @Autowired
    private InviteGroupRepository groups;

    private Event event;
    private InviteGroup family;

    @BeforeEach
    void prepareInvite() {
        event = events.saveAndFlush(Event.builder()
                .id(UUID.randomUUID())
                .title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Venue")
                .receptionLocation("Venue")
                .ceremonyMapsUrl("https://www.google.com/maps")
                .receptionMapsUrl("https://www.google.com/maps")
                .rsvpDeadline(LocalDate.of(2027, 10, 23))
                .createdAt(Instant.now())
                .build());
        family = saveGroup("Test family", "A1B2C3");
    }

    @ParameterizedTest
    @ValueSource(strings = {"A1B2C3", "a1b2c3", "  a1b2c3  "})
    void identifiesFamilyFromNormalizedCode(String code) {
        GuestIdentity identity = service.authenticate(code);

        assertThat(identity.getGroupId()).isEqualTo(family.getId());
        assertThat(identity.getEventId()).isEqualTo(event.getId());
        assertThat(groups.count()).isEqualTo(1);
    }

    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {"      ", "ABC12", "ABC1234", "AB!123", "ZZZZZZ"})
    void rejectsInvalidAndUnknownCodesWithTheSameError(String code) {
        assertThatThrownBy(() -> service.authenticate(code))
                .isInstanceOf(BadCredentialsException.class)
                .hasMessage("Invalid invitation code.");
        assertThat(groups.count()).isEqualTo(1);
    }

    @Test
    void differentCodesIdentifyDifferentFamilies() {
        InviteGroup secondFamily = saveGroup("Second family", "D4E5F6");

        assertThat(service.authenticate("A1B2C3").getGroupId()).isEqualTo(family.getId());
        assertThat(service.authenticate("D4E5F6").getGroupId()).isEqualTo(secondFamily.getId());
    }

    @Test
    void identityCanBeSerializedForJdbcSessionStorage() throws Exception {
        GuestIdentity identity = service.authenticate("A1B2C3");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(identity);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            GuestIdentity restored = (GuestIdentity) input.readObject();
            assertThat(restored.getGroupId()).isEqualTo(family.getId());
            assertThat(restored.getEventId()).isEqualTo(event.getId());
        }
    }

    private InviteGroup saveGroup(String name, String code) {
        return groups.saveAndFlush(InviteGroup.builder()
                .id(UUID.randomUUID())
                .event(event)
                .groupName(name)
                .inviteCode(code)
                .createdAt(Instant.now())
                .build());
    }
}
