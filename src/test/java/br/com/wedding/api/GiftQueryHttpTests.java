package br.com.wedding.api;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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
class GiftQueryHttpTests {

    private static final String ROUTE = "/api/admin/events/{eventId}/gifts";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EventRepository events;
    @Autowired
    private GiftRepository gifts;
    @Autowired
    private AdminAccountRepository accounts;

    private Event event;

    @BeforeEach
    void prepareAdminAndEvent() {
        accounts.saveAndFlush(new AdminAccount("will", "test-hash-placeholder", Instant.now()));
        event = events.saveAndFlush(Event.builder().id(UUID.randomUUID()).title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Venue").ceremonyMapsUrl("https://www.google.com/maps")
                .rsvpDeadline(LocalDate.of(2027, 10, 23)).createdAt(Instant.now()).build());
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void listsAllGiftsInNameAndIdOrderIncludingInactiveAndSoldOut() throws Exception {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        saveGift(UUID.randomUUID(), "Zulu gift", 2, true);
        saveGift(secondId, "Coffee machine", 1, true);
        saveGift(firstId, "Coffee machine", 0, false);

        mvc.perform(get(ROUTE, event.getId()))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(firstId.toString()))
                .andExpect(jsonPath("$[0].eventId").value(event.getId().toString()))
                .andExpect(jsonPath("$[0].name").value("Coffee machine"))
                .andExpect(jsonPath("$[0].description").value("For our home"))
                .andExpect(jsonPath("$[0].imageUrl").value("https://example.com/gift.jpg"))
                .andExpect(jsonPath("$[0].price").value(12.5))
                .andExpect(jsonPath("$[0].totalQuantity").value(2))
                .andExpect(jsonPath("$[0].availableQuantity").value(0))
                .andExpect(jsonPath("$[0].active").value(false))
                .andExpect(jsonPath("$[1].id").value(secondId.toString()))
                .andExpect(jsonPath("$[1].availableQuantity").value(1))
                .andExpect(jsonPath("$[1].active").value(true))
                .andExpect(jsonPath("$[2].name").value("Zulu gift"));
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void existingEventWithoutGiftsReturnsEmptyList() throws Exception {
        mvc.perform(get(ROUTE, event.getId())).andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void unknownEventReturnsNotFound() throws Exception {
        mvc.perform(get(ROUTE, UUID.randomUUID())).andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void rejectsInvalidEventIdentifier() throws Exception {
        mvc.perform(get(ROUTE, "invalid")).andExpect(status().isBadRequest());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(ROUTE, event.getId())).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "guest", roles = "GUEST")
    void rejectsGuestRole() throws Exception {
        mvc.perform(get(ROUTE, event.getId())).andExpect(status().isForbidden());
    }

    private void saveGift(UUID id, String name, int available, boolean active) {
        gifts.saveAndFlush(Gift.builder().id(id).event(event).name(name).description("For our home")
                .imageUrl("https://example.com/gift.jpg").price(new BigDecimal("12.50"))
                .totalQuantity(2).availableQuantity(available).active(active).build());
    }
}
