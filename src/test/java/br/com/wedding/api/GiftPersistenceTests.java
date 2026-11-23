package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import br.com.wedding.api.gift.Gift;
import br.com.wedding.api.gift.GiftRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@Transactional
class GiftPersistenceTests {

    @Autowired
    private GiftRepository gifts;
    @Autowired
    private EventRepository events;
    @Autowired
    private EntityManager entityManager;

    private Event event;

    @BeforeEach
    void prepareEvent() {
        event = events.saveAndFlush(Event.builder().id(UUID.randomUUID()).title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Venue").ceremonyMapsUrl("https://www.google.com/maps")
                .rsvpDeadline(LocalDate.of(2027, 10, 23)).createdAt(Instant.now()).build());
    }

    @Test
    void persistsGiftWithExactDecimalPriceAndEventReference() {
        Gift gift = gifts.saveAndFlush(Gift.builder().id(UUID.randomUUID()).event(event)
                .name("Coffee machine").description("A gift for our home")
                .imageUrl("https://example.com/coffee-machine.jpg")
                .price(new BigDecimal("349.90")).totalQuantity(2).availableQuantity(2).active(true).build());
        entityManager.clear();

        Gift saved = gifts.findById(gift.getId()).orElseThrow();
        assertThat(saved.getName()).isEqualTo("Coffee machine");
        assertThat(saved.getDescription()).isEqualTo("A gift for our home");
        assertThat(saved.getImageUrl()).isEqualTo("https://example.com/coffee-machine.jpg");
        assertThat(saved.getPrice()).isEqualByComparingTo("349.90");
        assertThat(saved.getTotalQuantity()).isEqualTo(2);
        assertThat(saved.getAvailableQuantity()).isEqualTo(2);
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getEvent().getId()).isEqualTo(event.getId());
    }

    @Test
    void permitsSoldOutInactiveGiftWithoutOptionalDescriptionOrImage() {
        Gift gift = gifts.saveAndFlush(gift("10.00", 1, 0, false));
        entityManager.clear();

        Gift saved = gifts.findById(gift.getId()).orElseThrow();
        assertThat(saved.getAvailableQuantity()).isZero();
        assertThat(saved.isActive()).isFalse();
        assertThat(saved.getDescription()).isNull();
        assertThat(saved.getImageUrl()).isNull();
    }

    @ParameterizedTest
    @CsvSource({"0.00,1,1", "-1.00,1,1", "10.00,0,0", "10.00,-1,0", "10.00,1,-1", "10.00,1,2"})
    void databaseRejectsInvalidPricesAndQuantities(String price, int total, int available) {
        assertThatThrownBy(() -> gifts.saveAndFlush(gift(price, total, available, true)))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void cannotDeleteEventWithRegisteredGift() {
        gifts.saveAndFlush(gift("10.00", 1, 1, true));
        entityManager.clear();
        assertThatThrownBy(() -> {
            events.deleteById(event.getId());
            events.flush();
        }).isInstanceOf(DataIntegrityViolationException.class);
    }

    private Gift gift(String price, int total, int available, boolean active) {
        return Gift.builder().id(UUID.randomUUID()).event(event).name("Test gift")
                .price(new BigDecimal(price)).totalQuantity(total).availableQuantity(available).active(active).build();
    }
}
