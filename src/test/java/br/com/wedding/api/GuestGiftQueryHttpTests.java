package br.com.wedding.api;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.security.core.authority.AuthorityUtils.createAuthorityList;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
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
import br.com.wedding.api.guest.InviteGroup;
import br.com.wedding.api.guest.InviteGroupRepository;
import br.com.wedding.api.guest.access.GuestIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@AutoConfigureMockMvc
@Transactional
class GuestGiftQueryHttpTests {

    private static final String ROUTE = "/api/guest/gifts";

    @Autowired
    private MockMvc mvc;
    @Autowired
    private EventRepository events;
    @Autowired
    private InviteGroupRepository groups;
    @Autowired
    private GiftRepository gifts;
    @Autowired
    private AdminAccountRepository accounts;

    private Event event;
    private InviteGroup group;
    private Authentication guest;

    @BeforeEach
    void prepareInvitation() {
        event = events.saveAndFlush(Event.builder().id(UUID.randomUUID()).title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Venue").ceremonyMapsUrl("https://www.google.com/maps")
                .rsvpDeadline(LocalDate.of(2027, 10, 23)).createdAt(Instant.now()).build());
        group = groups.saveAndFlush(InviteGroup.builder().id(UUID.randomUUID()).event(event)
                .groupName("Family").inviteCode("A1B2C3").createdAt(Instant.now()).build());
        guest = identity(group.getId(), event.getId());
    }

    @Test
    void listsOnlyActiveGiftsIncludingSoldOutWithGuestFields() throws Exception {
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        saveGift(UUID.randomUUID(), "Zulu gift", 2, true);
        saveGift(secondId, "Coffee machine", 0, true);
        saveGift(firstId, "Coffee machine", 3, true);
        saveGift(UUID.randomUUID(), "Hidden gift", 5, false);

        mvc.perform(get(ROUTE).with(authentication(guest)))
                .andExpect(status().isOk())
                .andExpect(header().string("Cache-Control", "no-store"))
                .andExpect(jsonPath("$", hasSize(3)))
                .andExpect(jsonPath("$[0].id").value(firstId.toString()))
                .andExpect(jsonPath("$[0].name").value("Coffee machine"))
                .andExpect(jsonPath("$[0].description").value("For our home"))
                .andExpect(jsonPath("$[0].imageUrl").value(nullValue()))
                .andExpect(jsonPath("$[0].price").value(349.9))
                .andExpect(jsonPath("$[0].availableQuantity").value(3))
                .andExpect(jsonPath("$[0].totalQuantity").doesNotExist())
                .andExpect(jsonPath("$[0].active").doesNotExist())
                .andExpect(jsonPath("$[0].eventId").doesNotExist())
                .andExpect(jsonPath("$[1].id").value(secondId.toString()))
                .andExpect(jsonPath("$[1].availableQuantity").value(0))
                .andExpect(jsonPath("$[2].name").value("Zulu gift"));
    }

    @Test
    void ignoresClientSuppliedEventAndGroupParameters() throws Exception {
        Gift gift = saveGift(UUID.randomUUID(), "Visible gift", 2, true);
        mvc.perform(get(ROUTE).with(authentication(guest))
                        .param("eventId", UUID.randomUUID().toString()).param("groupId", UUID.randomUUID().toString()))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].id").value(gift.getId().toString()));
    }

    @ParameterizedTest
    @ValueSource(booleans = {true, false})
    void noActiveGiftsReturnsEmptyList(boolean hasInactive) throws Exception {
        if (hasInactive) {
            saveGift(UUID.randomUUID(), "Hidden gift", 5, false);
        }
        mvc.perform(get(ROUTE).with(authentication(guest)))
                .andExpect(status().isOk()).andExpect(jsonPath("$", hasSize(0)));
    }

    @Test
    void unknownInvitationInvalidatesAccess() throws Exception {
        saveGift(UUID.randomUUID(), "Visible gift", 2, true);
        mvc.perform(get(ROUTE).with(authentication(identity(UUID.randomUUID(), event.getId()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void eventMustMatchInvitationIdentity() throws Exception {
        saveGift(UUID.randomUUID(), "Visible gift", 2, true);
        mvc.perform(get(ROUTE).with(authentication(identity(group.getId(), UUID.randomUUID()))))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void deletedInvitationCannotReadCatalog() throws Exception {
        saveGift(UUID.randomUUID(), "Visible gift", 2, true);
        groups.deleteById(group.getId());
        groups.flush();
        mvc.perform(get(ROUTE).with(authentication(guest))).andExpect(status().isUnauthorized());
    }

    @Test
    void requiresAuthentication() throws Exception {
        mvc.perform(get(ROUTE)).andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "will", roles = "ADMIN")
    void adminSessionCannotUseGuestEndpoint() throws Exception {
        accounts.saveAndFlush(new AdminAccount("will", "test-hash-placeholder", Instant.now()));
        mvc.perform(get(ROUTE)).andExpect(status().isForbidden());
    }

    @Test
    void readingCatalogDoesNotGrantAdminAccess() throws Exception {
        Gift gift = saveGift(UUID.randomUUID(), "Visible gift", 2, true);
        mvc.perform(get("/api/admin/events/{eventId}/gifts", event.getId()).with(authentication(guest)))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/events/{eventId}/gifts/{giftId}/active", event.getId(), gift.getId())
                        .with(authentication(guest)).with(csrf()).param("active", "false"))
                .andExpect(status().isForbidden());
    }

    private Authentication identity(UUID groupId, UUID eventId) {
        return UsernamePasswordAuthenticationToken.authenticated(
                new GuestIdentity(groupId, eventId), null, createAuthorityList("ROLE_GUEST"));
    }

    private Gift saveGift(UUID id, String name, int available, boolean active) {
        return gifts.saveAndFlush(Gift.builder().id(id).event(event).name(name).description("For our home")
                .price(new BigDecimal("349.90")).totalQuantity(5).availableQuantity(available).active(active).build());
    }
}
