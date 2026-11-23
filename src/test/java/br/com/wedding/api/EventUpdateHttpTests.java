package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.wedding.api.admin.AdminAccount;
import br.com.wedding.api.admin.AdminAccountRepository;
import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import br.com.wedding.api.guest.Guest;
import br.com.wedding.api.guest.GuestRepository;
import br.com.wedding.api.guest.InviteGroup;
import br.com.wedding.api.guest.InviteGroupRepository;
import br.com.wedding.api.guest.RsvpStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Transactional
class EventUpdateHttpTests {

    private static final String ROUTE = "/api/admin/events/{eventId}";
    private static final Instant CREATED_AT = Instant.parse("2026-11-20T12:00:00Z");
    private static final String REQUEST = """
            {
              "title": "  Updated wedding  ",
              "eventDate": "2027-11-20T15:00:00-03:00",
              "ceremonyLocation": "  New ceremony  ",
              "ceremonyMapsUrl": "https://www.google.com/maps?q=ceremony",
              "receptionLocation": "  New reception  ",
              "receptionMapsUrl": "https://www.google.com/maps?q=reception",
              "rsvpDeadline": "2027-10-23"
            }
            """;
    private final JsonMapper json = new JsonMapper();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EventRepository events;
    @Autowired
    private InviteGroupRepository groups;
    @Autowired
    private GuestRepository guests;
    @Autowired
    private AdminAccountRepository accounts;
    @Autowired
    private EntityManager entityManager;

    private UUID eventId;
    private UUID groupId;
    private UUID guestId;

    @BeforeEach
    void prepareWeddingWithInvitation() {
        accounts.saveAndFlush(new AdminAccount("will", "test-hash-placeholder", CREATED_AT));
        Event event = events.saveAndFlush(Event.builder().id(UUID.randomUUID()).title("Original wedding")
                .eventDate(Instant.parse("2027-06-12T18:00:00Z"))
                .ceremonyLocation("Old ceremony").ceremonyMapsUrl("https://www.google.com/maps?q=oldCeremony")
                .receptionLocation("Old reception").receptionMapsUrl("https://www.google.com/maps?q=oldReception")
                .rsvpDeadline(LocalDate.of(2027, 6, 1)).createdAt(CREATED_AT).build());
        eventId = event.getId();
        InviteGroup group = groups.saveAndFlush(InviteGroup.builder().id(UUID.randomUUID()).event(event)
                .groupName("Family").inviteCode("A1B2C3").createdAt(CREATED_AT).build());
        groupId = group.getId();
        guestId = guests.saveAndFlush(Guest.builder().id(UUID.randomUUID()).group(group).name("Ana")
                .rsvpStatus(RsvpStatus.CONFIRMED).respondedAt(CREATED_AT).build()).getId();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WithMockUser(username = "will", roles = "ADMIN")
    void updatesDetailsAndPreservesIdentityInvitationsAndResponses(boolean sameLocation) throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        if (sameLocation) {
            payload.remove("receptionLocation");
            payload.remove("receptionMapsUrl");
        }
        mvc.perform(put(ROUTE, eventId).param("receptionAtCeremonyLocation", String.valueOf(sameLocation))
                        .with(csrf()).contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(eventId.toString()))
                .andExpect(jsonPath("$.title").value("Updated wedding"))
                .andExpect(jsonPath("$.createdAt").value(CREATED_AT.toString()));

        entityManager.clear();
        Event saved = events.findById(eventId).orElseThrow();
        assertThat(saved.getEventDate()).isEqualTo(Instant.parse("2027-11-20T18:00:00Z"));
        assertThat(saved.getCeremonyLocation()).isEqualTo("New ceremony");
        assertThat(saved.getCeremonyMapsUrl()).isEqualTo("https://www.google.com/maps?q=ceremony");
        assertThat(saved.getRsvpDeadline()).isEqualTo(LocalDate.of(2027, 10, 23));
        assertThat(saved.getReceptionLocation()).isEqualTo(sameLocation ? null : "New reception");
        assertThat(saved.getReceptionMapsUrl()).isEqualTo(sameLocation ? null : "https://www.google.com/maps?q=reception");
        assertThat(saved.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(events.count()).isEqualTo(1);
        assertThat(groups.count()).isEqualTo(1);
        assertThat(groups.findById(groupId).orElseThrow().getEvent().getId()).isEqualTo(eventId);
        Guest guest = guests.findById(guestId).orElseThrow();
        assertThat(guests.count()).isEqualTo(1);
        assertThat(guest.getGroup().getId()).isEqualTo(groupId);
        assertThat(guest.getRsvpStatus()).isEqualTo(RsvpStatus.CONFIRMED);
        assertThat(guest.getRespondedAt()).isEqualTo(CREATED_AT);
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsMissingReceptionDetailsWithoutChangingEvent() throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.remove("receptionLocation");
        mvc.perform(put(ROUTE, eventId).param("receptionAtCeremonyLocation", "false").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        assertThat(events.findById(eventId).orElseThrow().getTitle()).isEqualTo("Original wedding");
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void validatesPayloadBeforeUpdating() throws Exception {
        mvc.perform(put(ROUTE, eventId).param("receptionAtCeremonyLocation", "true").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST.replace("Updated wedding", " ")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.title").exists());
        assertThat(events.findById(eventId).orElseThrow().getTitle()).isEqualTo("Original wedding");
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void unknownEventReturnsNotFoundWithoutCreatingAnother() throws Exception {
        mvc.perform(put(ROUTE, UUID.randomUUID()).param("receptionAtCeremonyLocation", "true").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isNotFound());
        assertThat(events.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresValidLocationFlag() throws Exception {
        mvc.perform(put(ROUTE, eventId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadRequest());
        mvc.perform(put(ROUTE, eventId).param("receptionAtCeremonyLocation", "invalid").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresValidCsrf() throws Exception {
        mvc.perform(put(ROUTE, eventId).param("receptionAtCeremonyLocation", "true")
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        mvc.perform(put(ROUTE, eventId).param("receptionAtCeremonyLocation", "true").with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(put(ROUTE, eventId).param("receptionAtCeremonyLocation", "true").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "guest", roles = "GUEST")
    void rejectsGuestRole() throws Exception {
        mvc.perform(put(ROUTE, eventId).param("receptionAtCeremonyLocation", "true").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsInvalidIdentifier() throws Exception {
        mvc.perform(put(ROUTE, "invalid").param("receptionAtCeremonyLocation", "true").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadRequest());
    }
}
