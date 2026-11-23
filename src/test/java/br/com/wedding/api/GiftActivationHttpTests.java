package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
class GiftActivationHttpTests {

    private static final String ROUTE = "/api/admin/events/{eventId}/gifts/{giftId}/active";

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
    void prepareAdminAndGift() {
        accounts.saveAndFlush(new AdminAccount("will", "test-hash-placeholder", Instant.now()));
        event = events.saveAndFlush(Event.builder().id(UUID.randomUUID()).title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Venue").ceremonyMapsUrl("https://www.google.com/maps")
                .rsvpDeadline(LocalDate.of(2027, 10, 23)).createdAt(Instant.now()).build());
        gift = saveGift(2);
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WithMockUser(username = "will", roles = "ADMIN")
    void setsExplicitStateAndPreservesDetailsAndQuantities(boolean active) throws Exception {
        gift.setActive(!active);
        gifts.saveAndFlush(gift);
        entityManager.clear();

        mvc.perform(patch(ROUTE, event.getId(), gift.getId()).param("active", String.valueOf(active)).with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(gift.getId().toString()))
                .andExpect(jsonPath("$.active").value(active))
                .andExpect(jsonPath("$.availableQuantity").value(2));
        mvc.perform(patch(ROUTE, event.getId(), gift.getId()).param("active", String.valueOf(active)).with(csrf()))
                .andExpect(status().isOk()).andExpect(jsonPath("$.active").value(active));
        mvc.perform(get("/api/admin/events/{eventId}/gifts", event.getId()))
                .andExpect(status().isOk()).andExpect(jsonPath("$[0].active").value(active));

        entityManager.clear();
        Gift saved = gifts.findById(gift.getId()).orElseThrow();
        assertThat(saved.isActive()).isEqualTo(active);
        assertThat(saved.getEvent().getId()).isEqualTo(event.getId());
        assertThat(saved.getName()).isEqualTo("Coffee machine");
        assertThat(saved.getDescription()).isEqualTo("For our home");
        assertThat(saved.getImageUrl()).isEqualTo("https://example.com/gift.jpg");
        assertThat(saved.getPrice()).isEqualByComparingTo("349.90");
        assertThat(saved.getTotalQuantity()).isEqualTo(5);
        assertThat(saved.getAvailableQuantity()).isEqualTo(2);
        assertThat(gifts.count()).isEqualTo(1);
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void reactivatingSoldOutGiftDoesNotRestoreQuantity() throws Exception {
        Gift soldOut = saveGift(0);
        mvc.perform(patch(ROUTE, event.getId(), soldOut.getId()).param("active", "true").with(csrf()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true))
                .andExpect(jsonPath("$.availableQuantity").value(0));
        entityManager.clear();
        assertThat(gifts.findById(soldOut.getId()).orElseThrow().getAvailableQuantity()).isZero();
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    @WithMockUser(username = "will", roles = "ADMIN")
    void unknownGiftOrWrongEventReturnsNotFound(boolean unknownGift) throws Exception {
        UUID eventId = unknownGift ? event.getId() : UUID.randomUUID();
        UUID giftId = unknownGift ? UUID.randomUUID() : gift.getId();
        mvc.perform(patch(ROUTE, eventId, giftId).param("active", "true").with(csrf()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.detail").value("The gift was not found in this event."));
        assertThat(gift.isActive()).isFalse();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresValidActiveParameter() throws Exception {
        mvc.perform(patch(ROUTE, event.getId(), gift.getId()).with(csrf()))
                .andExpect(status().isBadRequest());
        mvc.perform(patch(ROUTE, event.getId(), gift.getId()).param("active", "invalid").with(csrf()))
                .andExpect(status().isBadRequest());
        assertThat(gift.isActive()).isFalse();
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsInvalidIdentifier() throws Exception {
        mvc.perform(patch(ROUTE, event.getId(), "invalid").param("active", "true").with(csrf()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void requiresValidCsrf() throws Exception {
        mvc.perform(patch(ROUTE, event.getId(), gift.getId()).param("active", "true"))
                .andExpect(status().isForbidden());
        mvc.perform(patch(ROUTE, event.getId(), gift.getId()).param("active", "true").with(csrf().useInvalidToken()))
                .andExpect(status().isForbidden());
        assertThat(gift.isActive()).isFalse();
    }

    @Test
    @WithMockUser(username = "guest", roles = "GUEST")
    void rejectsGuestRole() throws Exception {
        mvc.perform(patch(ROUTE, event.getId(), gift.getId()).param("active", "true").with(csrf()))
                .andExpect(status().isForbidden());
        assertThat(gift.isActive()).isFalse();
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(patch(ROUTE, event.getId(), gift.getId()).param("active", "true").with(csrf()))
                .andExpect(status().isUnauthorized());
    }

    private Gift saveGift(int available) {
        return gifts.saveAndFlush(Gift.builder().id(UUID.randomUUID()).event(event).name("Coffee machine")
                .description("For our home").imageUrl("https://example.com/gift.jpg")
                .price(new BigDecimal("349.90")).totalQuantity(5).availableQuantity(available).active(false).build());
    }
}
