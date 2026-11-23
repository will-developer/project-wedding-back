package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
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
class GiftUpdateHttpTests {

    private static final String ROUTE = "/api/admin/events/{eventId}/gifts/{giftId}";
    private static final String REQUEST = """
            {
              "name": "  Updated gift  ",
              "description": "Updated description",
              "imageUrl": "https://example.com/new-image.jpg",
              "price": 299.90
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

    private Event event;
    private Gift gift;

    @BeforeEach
    void prepareAdminEventAndGift() {
        accounts.saveAndFlush(new AdminAccount("will", "test-hash-placeholder", Instant.now()));
        event = events.saveAndFlush(Event.builder().id(UUID.randomUUID()).title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Venue").ceremonyMapsUrl("https://www.google.com/maps")
                .rsvpDeadline(LocalDate.of(2027, 10, 23)).createdAt(Instant.now()).build());
        gift = saveGift(false, 1);
    }

    @ParameterizedTest
    @CsvSource({"true,0", "false,1"})
    @WithMockUser(username = "will", roles = "ADMIN")
    void updatesOnlyDetailsPreservingStockAndActivation(boolean active, int available) throws Exception {
        Gift target = saveGift(active, available);
        mvc.perform(put(ROUTE, event.getId(), target.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(target.getId().toString()))
                .andExpect(jsonPath("$.eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$.name").value("Updated gift"))
                .andExpect(jsonPath("$.price").value(299.9))
                .andExpect(jsonPath("$.totalQuantity").value(3))
                .andExpect(jsonPath("$.availableQuantity").value(available))
                .andExpect(jsonPath("$.active").value(active));

        entityManager.clear();
        Gift saved = gifts.findById(target.getId()).orElseThrow();
        assertThat(saved.getDescription()).isEqualTo("Updated description");
        assertThat(saved.getImageUrl()).isEqualTo("https://example.com/new-image.jpg");
        assertThat(saved.getPrice()).isEqualByComparingTo("299.90");
        assertThat(saved.getEvent().getId()).isEqualTo(event.getId());
        assertThat(saved.getTotalQuantity()).isEqualTo(3);
        assertThat(saved.getAvailableQuantity()).isEqualTo(available);
        assertThat(saved.isActive()).isEqualTo(active);
        assertThat(gifts.count()).isEqualTo(2);
        assertThat(gifts.findById(gift.getId()).orElseThrow().getName()).isEqualTo("Original gift");
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void clearsOmittedDescriptionAndEmptyImage() throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.remove("description");
        payload.put("imageUrl", "");
        mvc.perform(put(ROUTE, event.getId(), gift.getId()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.description").value(nullValue()))
                .andExpect(jsonPath("$.imageUrl").value(nullValue()));
        entityManager.clear();
        Gift saved = gifts.findById(gift.getId()).orElseThrow();
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getImageUrl()).isNull();
    }

    @ParameterizedTest
    @ValueSource(strings = {"0", "-1", "1.234", "100000000.00"})
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsInvalidPriceWithoutChangingGift(String price) throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.put("price", new BigDecimal(price));
        mvc.perform(put(ROUTE, event.getId(), gift.getId()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.price").exists());
        assertUnchanged();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresNameAndPrice() throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.put("name", "   ");
        payload.remove("price");
        mvc.perform(put(ROUTE, event.getId(), gift.getId()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errors.name").exists())
                .andExpect(jsonPath("$.errors.price").exists());
        assertUnchanged();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsNonHttpsImage() throws Exception {
        ObjectNode payload = (ObjectNode) json.readTree(REQUEST);
        payload.put("imageUrl", "http://example.com/image.jpg");
        mvc.perform(put(ROUTE, event.getId(), gift.getId()).with(csrf()).contentType(MediaType.APPLICATION_JSON)
                        .content(json.writeValueAsString(payload)))
                .andExpect(status().isBadRequest()).andExpect(jsonPath("$.errors.imageUrl").exists());
        assertUnchanged();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void unknownGiftReturnsNotFound() throws Exception {
        mvc.perform(put(ROUTE, event.getId(), UUID.randomUUID()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The gift was not found in this event."));
        assertThat(gifts.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void eventAndGiftMustMatch() throws Exception {
        mvc.perform(put(ROUTE, UUID.randomUUID(), gift.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The gift was not found in this event."));
        assertUnchanged();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsInvalidIdentifier() throws Exception {
        mvc.perform(put(ROUTE, event.getId(), "invalid").with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(put(ROUTE, event.getId(), gift.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "guest", roles = "GUEST")
    void rejectsGuestRole() throws Exception {
        mvc.perform(put(ROUTE, event.getId(), gift.getId()).with(csrf())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        assertUnchanged();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresValidCsrf() throws Exception {
        mvc.perform(put(ROUTE, event.getId(), gift.getId()).contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        mvc.perform(put(ROUTE, event.getId(), gift.getId()).with(csrf().useInvalidToken())
                        .contentType(MediaType.APPLICATION_JSON).content(REQUEST))
                .andExpect(status().isForbidden());
        assertUnchanged();
    }

    private Gift saveGift(boolean active, int available) {
        return gifts.saveAndFlush(Gift.builder().id(UUID.randomUUID()).event(event).name("Original gift")
                .description("Original description").imageUrl("https://example.com/old-image.jpg")
                .price(new BigDecimal("349.90")).totalQuantity(3).availableQuantity(available).active(active).build());
    }

    private void assertUnchanged() {
        assertThat(gift.getName()).isEqualTo("Original gift");
        assertThat(gift.getPrice()).isEqualByComparingTo("349.90");
        assertThat(gifts.count()).isEqualTo(1);
    }
}
