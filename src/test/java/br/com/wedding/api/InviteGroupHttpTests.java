package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.wedding.api.admin.AdminAccount;
import br.com.wedding.api.admin.AdminAccountRepository;
import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import br.com.wedding.api.invite.GuestRepository;
import br.com.wedding.api.invite.InviteGroupRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Transactional
class InviteGroupHttpTests {

    private static final String ROUTE = "/api/admin/events/{eventId}/invite-groups";
    private static final String REQUEST = """
            {
              "groupName": "  Test family  ",
              "notes": "One adult and one child",
              "guests": [
                {"name": "  Test adult  ", "isChild": false},
                {"name": "Test child", "isChild": true}
              ]
            }
            """;

    @Autowired
    private MockMvc mvc;
    @Autowired
    private AdminAccountRepository accounts;
    @Autowired
    private EventRepository events;
    @Autowired
    private InviteGroupRepository groups;
    @Autowired
    private GuestRepository guests;

    private UUID eventId;

    @BeforeEach
    void prepareAdminAndEvent() {
        accounts.saveAndFlush(new AdminAccount("will", "test-hash-placeholder", Instant.now()));
        eventId = events.saveAndFlush(Event.builder()
                .id(UUID.randomUUID())
                .title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Venue")
                .receptionLocation("Venue")
                .ceremonyMapsUrl("https://www.google.com/maps")
                .receptionMapsUrl("https://www.google.com/maps")
                .rsvpDeadline(LocalDate.of(2027, 10, 23))
                .createdAt(Instant.now())
                .build()).getId();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void listsFamiliesAndTheirGuestsInNameOrder() throws Exception {
        String zuluFamily = REQUEST.replace("Test family", "Zulu family")
                .replace("Test adult", "Zulu adult").replace("Test child", "Zulu child");
        String alphaFamily = REQUEST.replace("Test family", "Alpha family")
                .replace("Test adult", "Zoe adult").replace("Test child", "Ana child");
        mvc.perform(post(ROUTE, eventId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(zuluFamily))
                .andExpect(status().isCreated());
        mvc.perform(post(ROUTE, eventId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(alphaFamily))
                .andExpect(status().isCreated());

        mvc.perform(get(ROUTE, eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].groupName").value("Alpha family"))
                .andExpect(jsonPath("$[0].eventId").value(eventId.toString()))
                .andExpect(jsonPath("$[0].inviteCode").value(matchesPattern("[A-Z0-9]{6}")))
                .andExpect(jsonPath("$[0].guests.length()").value(2))
                .andExpect(jsonPath("$[0].guests[0].name").value("Ana child"))
                .andExpect(jsonPath("$[0].guests[0].isChild").value(true))
                .andExpect(jsonPath("$[0].guests[0].rsvpStatus").value("pending"))
                .andExpect(jsonPath("$[0].guests[1].name").value("Zoe adult"))
                .andExpect(jsonPath("$[1].groupName").value("Zulu family"))
                .andExpect(jsonPath("$[1].guests.length()").value(2))
                .andExpect(jsonPath("$[1].guests[0].name").value("Zulu adult"))
                .andExpect(jsonPath("$[1].guests[1].name").value("Zulu child"));
        assertThat(groups.count()).isEqualTo(2);
        assertThat(guests.count()).isEqualTo(4);
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void returnsEmptyListForEventWithoutFamilies() throws Exception {
        mvc.perform(get(ROUTE, eventId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void doesNotListFamiliesForMissingEvent() throws Exception {
        mvc.perform(post(ROUTE, eventId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated());
        mvc.perform(get(ROUTE, UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The event was not found."));
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsMalformedEventIdOnListing() throws Exception {
        mvc.perform(get(ROUTE, "invalid-id"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthenticationToList() throws Exception {
        mvc.perform(get(ROUTE, eventId))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "will", roles = "GUEST")
    void requiresAdminRoleToList() throws Exception {
        mvc.perform(get(ROUTE, eventId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void createsFamilyWithGuestsAndGeneratedCode() throws Exception {
        mvc.perform(post(ROUTE, eventId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.groupName").value("Test family"))
                .andExpect(jsonPath("$.inviteCode").value(matchesPattern("[A-Z0-9]{6}")))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andExpect(jsonPath("$.guests.length()").value(2))
                .andExpect(jsonPath("$.guests[0].name").value("Test adult"))
                .andExpect(jsonPath("$.guests[0].isChild").value(false))
                .andExpect(jsonPath("$.guests[1].isChild").value(true))
                .andExpect(jsonPath("$.guests[0].rsvpStatus").value("pending"));
        assertThat(groups.count()).isEqualTo(1);
        assertThat(guests.count()).isEqualTo(2);
    }

    @ParameterizedTest
    @CsvSource({
            "'{\"groupName\":\" \",\"guests\":[{\"name\":\"Adult\",\"isChild\":false}]}',groupName",
            "'{\"groupName\":\"Family\",\"guests\":[]}',guests",
            "'{\"groupName\":\"Family\",\"guests\":[{\"name\":\"Adult\"}]}',guests[0].isChild",
            "'{\"groupName\":\"Family\",\"guests\":[{\"name\":\" \",\"isChild\":false}]}',guests[0].name"
    })
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsInvalidInputWithoutSaving(String body, String field) throws Exception {
        mvc.perform(post(ROUTE, eventId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors['" + field + "']").exists());
        assertThat(groups.count()).isZero();
        assertThat(guests.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsMissingEvent() throws Exception {
        mvc.perform(post(ROUTE, UUID.randomUUID()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The event was not found."));
        assertThat(groups.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsMalformedUuidAndJson() throws Exception {
        mvc.perform(post(ROUTE, "invalid-id").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadRequest());
        mvc.perform(post(ROUTE, eventId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        assertThat(groups.count()).isZero();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post(ROUTE, eventId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
        assertThat(groups.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "GUEST")
    void requiresAdminRole() throws Exception {
        mvc.perform(post(ROUTE, eventId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        assertThat(groups.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresCsrf() throws Exception {
        mvc.perform(post(ROUTE, eventId).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        assertThat(groups.count()).isZero();
    }
}
