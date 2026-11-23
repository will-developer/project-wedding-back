package br.com.wedding.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.wedding.api.admin.AdminAccount;
import br.com.wedding.api.admin.AdminAccountRepository;
import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Transactional
class EventQueryHttpTests {

    private static final String ROUTE = "/api/admin/events";
    private static final Instant CREATED_AT = Instant.parse("2026-11-20T12:00:00Z");

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EventRepository events;
    @Autowired
    private AdminAccountRepository accounts;

    @BeforeEach
    void prepareAdmin() {
        accounts.saveAndFlush(new AdminAccount("will", "test-hash-placeholder", CREATED_AT));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WithMockUser(username = "will", roles = "ADMIN")
    void returnsEventDetailsWithoutRequiringCsrf(boolean sharedReception) throws Exception {
        Event event = events.saveAndFlush(Event.builder().id(UUID.randomUUID()).title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Ceremony venue").ceremonyMapsUrl("https://www.google.com/maps?q=ceremony")
                .receptionLocation(sharedReception ? null : "Reception venue")
                .receptionMapsUrl(sharedReception ? null : "https://www.google.com/maps?q=reception")
                .rsvpDeadline(LocalDate.of(2027, 10, 23)).createdAt(CREATED_AT).build());

        mvc.perform(get(ROUTE))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(event.getId().toString()))
                .andExpect(jsonPath("$[0].title").value("Test wedding"))
                .andExpect(jsonPath("$[0].eventDate").value("2027-11-20T18:00:00Z"))
                .andExpect(jsonPath("$[0].ceremonyLocation").value("Ceremony venue"))
                .andExpect(jsonPath("$[0].ceremonyMapsUrl").value("https://www.google.com/maps?q=ceremony"))
                .andExpect(sharedReception ? jsonPath("$[0].receptionLocation").value(nullValue())
                        : jsonPath("$[0].receptionLocation").value("Reception venue"))
                .andExpect(sharedReception ? jsonPath("$[0].receptionMapsUrl").value(nullValue())
                        : jsonPath("$[0].receptionMapsUrl").value("https://www.google.com/maps?q=reception"))
                .andExpect(jsonPath("$[0].rsvpDeadline").value("2027-10-23"))
                .andExpect(jsonPath("$[0].createdAt").value(CREATED_AT.toString()));
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void returnsEmptyListBeforeEventRegistration() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "guest", roles = "GUEST")
    void rejectsGuestRole() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isForbidden());
    }
}
