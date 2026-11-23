package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.wedding.api.admin.AdminAccount;
import br.com.wedding.api.admin.AdminAccountRepository;
import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import br.com.wedding.api.gift.Gift;
import br.com.wedding.api.gift.GiftRepository;
import jakarta.persistence.EntityManager;
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
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import tools.jackson.databind.json.JsonMapper;
import tools.jackson.databind.node.ObjectNode;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Transactional
class GiftRegistrationHttpTests {

    private static final String ROUTE = "/api/admin/events/{eventId}/gifts";
    private static final String REQUEST = """
            {
              "name": "  Coffee machine  ",
              "description": "A gift for our home",
              "imageUrl": "https://example.com/coffee-machine.jpg",
              "price": 349.90,
              "totalQuantity": 2
            }
            """;
    private final JsonMapper json = new JsonMapper();

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EventRepository events;
    @Autowired
    private GiftRepository gifts;
    @Autowired
    private AdminAccountRepository accounts;
    @Autowired
    private EntityManager entityManager;

    private UUID eventId;

    @BeforeEach
    void prepareAdminAndEvent() {
        accounts.saveAndFlush(new AdminAccount("will", "test-hash-placeholder", Instant.now()));
        eventId = events.saveAndFlush(Event.builder().id(UUID.randomUUID()).title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Venue").ceremonyMapsUrl("https://www.google.com/maps")
                .rsvpDeadline(LocalDate.of(2027, 10, 23)).createdAt(Instant.now()).build()).getId();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void createsActiveGiftWithServerDefinedAvailableQuantity() throws Exception {
        String body = mvc.perform(post(ROUTE, eventId).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value(eventId.toString()))
                .andExpect(jsonPath("$.name").value("Coffee machine"))
                .andExpect(jsonPath("$.price").value(349.9))
                .andExpect(jsonPath("$.totalQuantity").value(2))
                .andExpect(jsonPath("$.availableQuantity").value(2))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn().getResponse().getContentAsString();
        UUID id = UUID.fromString(json.readTree(body).path("id").asText());
        entityManager.clear();
        Gift saved = gifts.findById(id).orElseThrow();
        assertThat(saved.getEvent().getId()).isEqualTo(eventId);
        assertThat(saved.getPrice()).isEqualByComparingTo("349.90");
        assertThat(saved.getDescription()).isEqualTo("A gift for our home");
        assertThat(saved.getImageUrl()).isEqualTo("https://example.com/coffee-machine.jpg");
        assertThat(saved.getAvailableQuantity()).isEqualTo(saved.getTotalQuantity());
        assertThat(saved.isActive()).isTrue();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void permitsMissingDescriptionAndEmptyImage() throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.remove("description");
        payload.put("imageUrl", "");
        mvc.perform(post(ROUTE, eventId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isCreated());
        entityManager.clear();
        Gift saved = gifts.findAll().getFirst();
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getImageUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"name", "price", "totalQuantity"})
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresMandatoryFields(String field) throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.remove(field);
        mvc.perform(post(ROUTE, eventId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + field).exists());
        assertThat(gifts.count()).isZero();
    }

    @ParameterizedTest
    @CsvSource({"price,0", "price,-0.01", "price,1.234", "price,100000000.00", "totalQuantity,0", "totalQuantity,-1"})
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsInvalidAmountsAndQuantities(String field, String value) throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        if ("price".equals(field)) {
            payload.put(field, new BigDecimal(value));
        } else {
            payload.put(field, Integer.parseInt(value));
        }
        mvc.perform(post(ROUTE, eventId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors." + field).exists());
        assertThat(gifts.count()).isZero();
    }

    @ParameterizedTest
    @ValueSource(strings = {"http://example.com/image.jpg", "not-a-url", "javascript:alert(1)"})
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresHttpsForProvidedImage(String url) throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.put("imageUrl", url);
        mvc.perform(post(ROUTE, eventId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.imageUrl").exists());
        assertThat(gifts.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsBlankOrOversizedName() throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.put("name", "   ");
        mvc.perform(post(ROUTE, eventId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        payload.put("name", "a".repeat(161));
        mvc.perform(post(ROUTE, eventId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest());
        assertThat(gifts.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsUnparseablePriceWithGenericFormatMessage() throws Exception {
        mvc.perform(post(ROUTE, eventId).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(REQUEST.replace("349.90", "\"invalid\"")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.detail").value("Invalid JSON or field format."));
        assertThat(gifts.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void unknownEventReturnsNotFound() throws Exception {
        mvc.perform(post(ROUTE, UUID.randomUUID()).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isNotFound());
        assertThat(gifts.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void invalidIdentifierReturnsBadRequest() throws Exception {
        mvc.perform(post(ROUTE, "invalid").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(post(ROUTE, eventId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "guest", roles = "GUEST")
    void rejectsGuestRole() throws Exception {
        mvc.perform(post(ROUTE, eventId).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        assertThat(gifts.count()).isZero();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresValidCsrf() throws Exception {
        mvc.perform(post(ROUTE, eventId).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        mvc.perform(post(ROUTE, eventId).with(csrf().useInvalidToken()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        assertThat(gifts.count()).isZero();
    }
}
