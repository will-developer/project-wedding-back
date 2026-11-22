package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import br.com.wedding.api.admin.AdminAccount;
import br.com.wedding.api.admin.AdminAccountRepository;
import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
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
class EventRegistrationTests {

    private static final String REQUEST = """
            {
              "title": "  Test wedding  ",
              "eventDate": "2027-06-12T15:00:00-03:00",
              "ceremonyLocation": "Same venue",
              "receptionLocation": "Same venue",
              "ceremonyMapsUrl": "https://maps.app.goo.gl/testVenue",
              "receptionMapsUrl": "https://maps.app.goo.gl/testVenue",
              "rsvpDeadline": "2027-06-01"
            }
            """;
    private final JsonMapper json = new JsonMapper();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EventRepository events;
    @Autowired
    private AdminAccountRepository accounts;
    @Autowired
    private EntityManager entityManager;

    @BeforeEach
    void prepareAdmin() {
        accounts.saveAndFlush(new AdminAccount("will", "test-hash-placeholder", Instant.now()));
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void createsEventWithIdenticalLocationsAndMapsUrls() throws Exception {
        String response = mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Test wedding"))
                .andExpect(jsonPath("$.ceremonyMapsUrl").value("https://maps.app.goo.gl/testVenue"))
                .andExpect(jsonPath("$.receptionMapsUrl").value("https://maps.app.goo.gl/testVenue"))
                .andExpect(jsonPath("$.createdAt").isNotEmpty())
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(response).path("id").asText());
        entityManager.clear();
        Event saved = events.findById(id).orElseThrow();
        assertThat(saved.getEventDate()).isEqualTo(Instant.parse("2027-06-12T18:00:00Z"));
        assertThat(saved.getReceptionLocation()).isEqualTo(saved.getCeremonyLocation());
        assertThat(saved.getReceptionMapsUrl()).isEqualTo(saved.getCeremonyMapsUrl());
    }

    @ParameterizedTest
    @ValueSource(strings = {"missing", "null", "empty"})
    @WithMockUser(username = "will", roles = "ADMIN")
    void sharedLocationStoresNullReceptionFields(String input) throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        if ("missing".equals(input)) {
            payload.remove("receptionLocation");
            payload.remove("receptionMapsUrl");
        } else if ("null".equals(input)) {
            payload.putNull("receptionLocation");
            payload.putNull("receptionMapsUrl");
        } else {
            payload.put("receptionLocation", "");
            payload.put("receptionMapsUrl", "");
        }

        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "true").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receptionLocation").value(nullValue()))
                .andExpect(jsonPath("$.receptionMapsUrl").value(nullValue()));
        entityManager.clear();
        Event saved = events.findAll().getFirst();
        assertThat(saved.getReceptionLocation()).isNull();
        assertThat(saved.getReceptionMapsUrl()).isNull();
        assertThat(saved.getCeremonyLocation()).isEqualTo("Same venue");
        assertThat(saved.getCeremonyMapsUrl()).isEqualTo("https://maps.app.goo.gl/testVenue");
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void sharedLocationFlagTakesPrecedenceOverProvidedReceptionFields() throws Exception {
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "true").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receptionLocation").value(nullValue()))
                .andExpect(jsonPath("$.receptionMapsUrl").value(nullValue()));
        entityManager.clear();
        assertThat(events.findAll().getFirst().getReceptionLocation()).isNull();
        assertThat(events.findAll().getFirst().getReceptionMapsUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"receptionLocation", "receptionMapsUrl"})
    @WithMockUser(username = "will", roles = "ADMIN")
    void separateLocationRequiresBothReceptionFields(String field) throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.remove(field);
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value(
                        "Reception location and Maps URL are required when the reception is at a different location."));
        payload.putNull(field);
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        payload.put(field, "   ");
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        assertThat(events.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void separateLocationRejectsBothReceptionFieldsMissing() throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.remove("receptionLocation");
        payload.remove("receptionMapsUrl");
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        assertThat(events.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void separateLocationPreservesReceptionDetails() throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.put("receptionLocation", "  Reception venue  ");
        payload.put("receptionMapsUrl", "https://maps.app.goo.gl/reception");
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.receptionLocation").value("Reception venue"))
                .andExpect(jsonPath("$.receptionMapsUrl").value("https://maps.app.goo.gl/reception"));
        entityManager.clear();
        Event saved = events.findAll().getFirst();
        assertThat(saved.getReceptionLocation()).isEqualTo("Reception venue");
        assertThat(saved.getReceptionMapsUrl()).isEqualTo("https://maps.app.goo.gl/reception");
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresValidLocationFlagParameter() throws Exception {
        mvc.perform(post("/api/admin/events").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "invalid").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadRequest());
        assertThat(events.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"Nosso casamento", "Outro casamento"})
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsSecondEventEvenWithDifferentTitle(String secondTitle) throws Exception {
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated());
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.put("title", secondTitle);
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.detail").value(
                        "A wedding is already registered. Only one event is allowed."));
    }

    @ParameterizedTest
    @ValueSource(strings = {"ceremonyLocation", "ceremonyMapsUrl", "title"})
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsMissingOrBlankRequiredTextWithoutSaving(String field) throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.remove(field);
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + field).exists());
        payload.put(field, "   ");
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        assertThat(events.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://maps.google.com/maps", "javascript:alert(1)", "not-a-url"})
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsInvalidOrNonHttpsUrls(String url) throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.put("ceremonyMapsUrl", url);
        payload.put("receptionMapsUrl", url);
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.ceremonyMapsUrl").exists())
                .andExpect(jsonPath("$.errors.receptionMapsUrl").exists());
        assertThat(events.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresRsvpDeadline() throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.remove("rsvpDeadline");
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.rsvpDeadline").exists());
        assertThat(events.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsMalformedDate() throws Exception {
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("2027-06-12T15:00:00-03:00", "12/06/2027")))
                .andExpect(status().isBadRequest());
        assertThat(events.count()).isZero();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
        assertThat(events.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "GUEST")
    void rejectsNonAdminIdentity() throws Exception {
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        assertThat(events.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresCsrf() throws Exception {
        mvc.perform(post("/api/admin/events").param("receptionAtCeremonyLocation", "false").contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        assertThat(events.count()).isZero();
    }
}
