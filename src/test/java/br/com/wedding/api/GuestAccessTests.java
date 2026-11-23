package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import br.com.wedding.api.guest.InviteGroup;
import br.com.wedding.api.guest.InviteGroupRepository;
import br.com.wedding.api.guest.access.GuestAccessService;
import br.com.wedding.api.guest.access.GuestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Transactional
class GuestAccessTests {

    @Autowired
    private GuestAccessService service;
    @Autowired
    private EventRepository events;
    @Autowired
    private InviteGroupRepository groups;
    @Autowired
    private MockMvc mvc;

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
        Authentication result = service.authenticate(code);
        GuestIdentity identity = (GuestIdentity) result.getPrincipal();

        assertThat(identity.getGroupId()).isEqualTo(family.getId());
        assertThat(identity.getEventId()).isEqualTo(event.getId());
        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo(family.getId().toString());
        assertThat(result.getCredentials()).isNull();
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactly("ROLE_GUEST");
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

        GuestIdentity first = (GuestIdentity) service.authenticate("A1B2C3").getPrincipal();
        GuestIdentity second = (GuestIdentity) service.authenticate("D4E5F6").getPrincipal();
        assertThat(first.getGroupId()).isEqualTo(family.getId());
        assertThat(second.getGroupId()).isEqualTo(secondFamily.getId());
    }

    @Test
    void authenticationCanBeSerializedForJdbcSessionStorage() throws Exception {
        Authentication result = service.authenticate("A1B2C3");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ObjectOutputStream output = new ObjectOutputStream(bytes)) {
            output.writeObject(result);
        }
        try (ObjectInputStream input = new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray()))) {
            Authentication restored = (Authentication) input.readObject();
            GuestIdentity identity = (GuestIdentity) restored.getPrincipal();
            assertThat(identity.getGroupId()).isEqualTo(family.getId());
            assertThat(identity.getEventId()).isEqualTo(event.getId());
            assertThat(restored.isAuthenticated()).isTrue();
            assertThat(restored.getCredentials()).isNull();
            assertThat(restored.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                    .containsExactly("ROLE_GUEST");
        }
    }

    @Test
    void authenticatedGuestCannotAccessAdminRoutes() throws Exception {
        Authentication guest = service.authenticate("A1B2C3");

        mvc.perform(get("/api/admin/events/{eventId}/invite-groups", event.getId()).with(authentication(guest)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/auth/me").with(authentication(guest)))
                .andExpect(status().isForbidden());
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
