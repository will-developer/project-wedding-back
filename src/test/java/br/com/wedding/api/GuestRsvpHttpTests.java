package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
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
import br.com.wedding.api.guest.access.GuestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Transactional
class GuestRsvpHttpTests {

    private static final String ROUTE = "/api/guest/guests/{guestId}/rsvp";
    private static final Instant NOW = Instant.parse("2027-10-23T15:00:00Z");

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
    @MockitoBean
    private Clock clock;

    private Guest guest;
    private Guest otherGuest;
    private Authentication guestAuthentication;

    @BeforeEach
    void prepareInvitationAndSession() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneId.of("America/Sao_Paulo"));
        Event event = events.saveAndFlush(Event.builder()
                .id(UUID.randomUUID()).title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Venue").receptionLocation("Venue")
                .ceremonyMapsUrl("https://www.google.com/maps").receptionMapsUrl("https://www.google.com/maps")
                .rsvpDeadline(LocalDate.of(2027, 10, 23)).createdAt(NOW).build());
        InviteGroup family = saveGroup(event, "First family", "A1B2C3");
        guest = saveGuest(family, "Ana");
        otherGuest = saveGuest(saveGroup(event, "Other family", "D4E5F6"), "Private guest");

        guestAuthentication = UsernamePasswordAuthenticationToken.authenticated(
                new GuestIdentity(family.getId(), event.getId()), null, createAuthorityList("ROLE_GUEST"));
    }

    @ParameterizedTest
    @CsvSource({"true,confirmed", "false,declined"})
    void respondsUsingGuestSessionAndReturnsUpdatedGuest(boolean attending, String expected) throws Exception {
        mvc.perform(put(ROUTE, guest.getId()).with(authentication(guestAuthentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attending\":" + attending + "}"))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$.id").value(guest.getId().toString()))
                .andExpect(jsonPath("$.rsvpStatus").value(expected))
                .andExpect(jsonPath("$.respondedAt").value(NOW.toString()));

        assertThat(guests.findById(guest.getId()).orElseThrow().getRsvpStatus().getDatabaseValue()).isEqualTo(expected);
        assertThat(otherGuest.getRsvpStatus()).isEqualTo(RsvpStatus.PENDING);
    }

    @ParameterizedTest
    @ValueSource(strings = {"{}", "{\"attending\":null}"})
    void requiresExplicitAttendingValue(String body) throws Exception {
        mvc.perform(put(ROUTE, guest.getId()).with(authentication(guestAuthentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.attending").exists());
        assertUnanswered();
    }

    @Test
    void rejectsMalformedJsonAndInvalidGuestId() throws Exception {
        mvc.perform(put(ROUTE, guest.getId()).with(authentication(guestAuthentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{"))
                .andExpect(status().isBadRequest());
        mvc.perform(put(ROUTE, "not-a-uuid").with(authentication(guestAuthentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attending\":true}"))
                .andExpect(status().isBadRequest());
        assertUnanswered();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void anotherFamilyAndUnknownGuestReturnSameNotFoundResponse(boolean existingGuest) throws Exception {
        UUID id = existingGuest ? otherGuest.getId() : UUID.randomUUID();
        mvc.perform(put(ROUTE, id).with(authentication(guestAuthentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attending\":true}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The guest was not found in this invitation."));
        assertThat(otherGuest.getRsvpStatus()).isEqualTo(RsvpStatus.PENDING);
        assertUnanswered();
    }

    @Test
    void expiredDeadlineReturnsConflictWithoutSavingResponse() throws Exception {
        when(clock.instant()).thenReturn(Instant.parse("2027-10-24T03:00:00Z"));
        mvc.perform(put(ROUTE, guest.getId()).with(authentication(guestAuthentication)).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attending\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value("The RSVP deadline has passed."));
        mvc.perform(get("/api/guest/invitation").with(authentication(guestAuthentication)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.guests[0].rsvpStatus").value("expired"))
                .andExpect(jsonPath("$.guests[0].respondedAt").value(nullValue()));
        assertUnanswered();
    }

    @Test
    void rejectsMissingAndInvalidCsrfEvenWithGuestSession() throws Exception {
        mvc.perform(put(ROUTE, guest.getId()).with(authentication(guestAuthentication))
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attending\":true}"))
                .andExpect(status().isForbidden());
        mvc.perform(put(ROUTE, guest.getId()).with(authentication(guestAuthentication)).with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attending\":true}"))
                .andExpect(status().isForbidden());
        assertUnanswered();
    }

    @Test
    void requiresAuthenticationEvenWithValidCsrf() throws Exception {
        mvc.perform(put(ROUTE, guest.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attending\":true}"))
                .andExpect(status().isUnauthorized());
        assertUnanswered();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void adminRoleCannotUseGuestRsvpEndpoint() throws Exception {
        accounts.saveAndFlush(new AdminAccount("will", "test-hash-placeholder", NOW));
        mvc.perform(put(ROUTE, guest.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"attending\":true}"))
                .andExpect(status().isForbidden());
        assertUnanswered();
    }

    private void assertUnanswered() {
        assertThat(guest.getRsvpStatus()).isEqualTo(RsvpStatus.PENDING);
        assertThat(guest.getRespondedAt()).isNull();
    }

    private InviteGroup saveGroup(Event event, String name, String code) {
        return groups.saveAndFlush(InviteGroup.builder().id(UUID.randomUUID()).event(event)
                .groupName(name).inviteCode(code).createdAt(NOW).build());
    }

    private Guest saveGuest(InviteGroup group, String name) {
        return guests.saveAndFlush(Guest.builder().id(UUID.randomUUID()).group(group)
                .name(name).rsvpStatus(RsvpStatus.PENDING).build());
    }
}
