package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import br.com.wedding.api.event.Event;
import br.com.wedding.api.event.EventRepository;
import br.com.wedding.api.common.exceptions.ApplicationExceptions.EventNotFoundException;
import br.com.wedding.api.guest.GuestRepository;
import br.com.wedding.api.guest.InviteCodeGenerator;
import br.com.wedding.api.guest.InviteGroupRepository;
import br.com.wedding.api.guest.InviteGroupService;
import br.com.wedding.api.guest.dto.CreateGuestRequest;
import br.com.wedding.api.guest.dto.CreateInviteGroupRequest;
import br.com.wedding.api.guest.dto.InviteGroupResponse;
import jakarta.validation.ConstraintViolationException;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
class InviteRegistrationTests {

    @Autowired
    private InviteGroupService service;
    @Autowired
    private EventRepository events;
    @MockitoSpyBean
    private InviteGroupRepository groups;
    @Autowired
    private JdbcTemplate jdbc;
    @MockitoSpyBean
    private GuestRepository guests;
    @MockitoSpyBean
    private InviteCodeGenerator codes;

    private UUID eventId;

    @BeforeEach
    void createEvent() {
        cleanData();
        eventId = events.saveAndFlush(Event.builder()
                .id(UUID.randomUUID()).title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Venue").receptionLocation("Venue")
                .ceremonyMapsUrl("https://www.google.com/maps")
                .receptionMapsUrl("https://www.google.com/maps")
                .rsvpDeadline(LocalDate.of(2027, 10, 23))
                .createdAt(Instant.now()).build()).getId();
    }

    @AfterEach
    void cleanData() {
        jdbc.update("DELETE FROM guest");
        jdbc.update("DELETE FROM invite_group");
        jdbc.update("DELETE FROM event");
    }

    @Test
    void savesFamilyAndGuestsWithGeneratedCodeAndPendingStatus() throws Exception {
        InviteGroupResponse response = service.create(eventId, request());
        assertThat(response.getInviteCode()).matches("[A-Z0-9]{6}");
        assertThat(response.getGroupName()).isEqualTo("Test family");
        assertThat(response.getEventId()).isEqualTo(eventId);
        assertThat(response.getGuests()).hasSize(2).allSatisfy(guest -> {
            assertThat(guest.getRsvpStatus()).isEqualTo("pending");
            assertThat(guest.getRespondedAt()).isNull();
        });
        assertThat(groups.count()).isEqualTo(1);
        assertThat(guests.count()).isEqualTo(2);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM guest WHERE group_id = ? AND rsvp_status = 'pending'",
                Long.class, response.getId())).isEqualTo(2L);
        var json = new JsonMapper().readTree(new JsonMapper().writeValueAsString(response));
        assertThat(json.path("guests").get(0).path("isChild").asBoolean()).isFalse();
        assertThat(json.path("guests").get(1).path("isChild").asBoolean()).isTrue();
        assertThat(json.path("guests").get(0).has("child")).isFalse();
    }

    @Test
    void retriesOnlyTheCodeWhenItAlreadyExists() {
        doReturn("AAAAAA", "AAAAAA", "BBBBBB").when(codes).generate();
        service.create(eventId, request());
        InviteGroupResponse retried = service.create(eventId, request());

        assertThat(retried.getInviteCode()).isEqualTo("BBBBBB");
        assertThat(groups.count()).isEqualTo(2);
        assertThat(guests.count()).isEqualTo(4);
    }

    @Test
    void continuesAfterMoreThanFiveCollisionsUntilCodeIsAvailable() {
        doReturn("AAAAAA").when(codes).generate();
        service.create(eventId, request());
        doReturn("AAAAAA", "AAAAAA", "AAAAAA", "AAAAAA", "AAAAAA", "AAAAAA", "BBBBBB")
                .when(codes).generate();

        InviteGroupResponse response = service.create(eventId, request());

        assertThat(response.getInviteCode()).isEqualTo("BBBBBB");
        verify(codes, times(8)).generate();
        assertThat(groups.count()).isEqualTo(2);
        assertThat(guests.count()).isEqualTo(4);
    }

    @Test
    void guestPersistenceFailureRollsBackFamilyAndDoesNotRetry() {
        doThrow(new DataIntegrityViolationException("Test guest failure")).when(guests).saveAll(any());

        assertThatThrownBy(() -> service.create(eventId, request()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(groups.count()).isZero();
        assertThat(guests.count()).isZero();
        verify(codes, times(1)).generate();
    }

    @Test
    void rejectsMissingEventWithoutSaving() {
        assertThatThrownBy(() -> service.create(UUID.randomUUID(), request()))
                .isInstanceOf(EventNotFoundException.class);
        assertThat(groups.count()).isZero();
        assertThat(guests.count()).isZero();
    }

    @Test
    void rejectsInvalidGuestInputWithoutSaving() {
        CreateInviteGroupRequest invalid = request();
        invalid.getGuests().get(0).setIsChild(null);
        assertThatThrownBy(() -> service.create(eventId, invalid))
                .isInstanceOf(ConstraintViolationException.class);
        invalid.setGuests(List.of());
        assertThatThrownBy(() -> service.create(eventId, invalid))
                .isInstanceOf(ConstraintViolationException.class);
        assertThat(groups.count()).isZero();
    }

    @Test
    void doesNotIgnoreOtherDatabaseConstraints() {
        doReturn("abc123").when(codes).generate();

        assertThatThrownBy(() -> service.create(eventId, request()))
                .isInstanceOf(DataIntegrityViolationException.class);
        assertThat(groups.count()).isZero();
        assertThat(guests.count()).isZero();
        verify(codes, times(1)).generate();
    }

    @Test
    void databaseRejectsConcurrentDuplicateCodeWithoutPartialRegistration() throws Exception {
        CyclicBarrier checkedAvailability = new CyclicBarrier(2);
        doReturn("AAAAAA").when(codes).generate();
        doAnswer(invocation -> {
            boolean exists = Boolean.TRUE.equals(jdbc.queryForObject(
                    "SELECT EXISTS (SELECT 1 FROM invite_group WHERE invite_code = ?)",
                    Boolean.class, invocation.getArgument(0, String.class)));
            checkedAvailability.await(10, TimeUnit.SECONDS);
            return exists;
        }).when(groups).existsByInviteCode("AAAAAA");

        var executor = Executors.newFixedThreadPool(2);
        try {
            var first = executor.submit(() -> service.create(eventId, request()));
            var second = executor.submit(() -> service.create(eventId, request()));
            int successfulRegistrations = 0;
            int rejectedRegistrations = 0;
            for (var result : List.of(first, second)) {
                try {
                    assertThat(result.get(20, TimeUnit.SECONDS).getInviteCode()).isEqualTo("AAAAAA");
                    successfulRegistrations++;
                } catch (ExecutionException exception) {
                    assertThat(exception.getCause()).isInstanceOf(DataIntegrityViolationException.class);
                    rejectedRegistrations++;
                }
            }
            assertThat(successfulRegistrations).isEqualTo(1);
            assertThat(rejectedRegistrations).isEqualTo(1);
            assertThat(groups.count()).isEqualTo(1);
            assertThat(guests.count()).isEqualTo(2);
        } finally {
            executor.shutdownNow();
        }
    }

    private CreateInviteGroupRequest request() {
        CreateGuestRequest adult = new CreateGuestRequest();
        adult.setName("  Test adult  ");
        adult.setIsChild(false);
        CreateGuestRequest child = new CreateGuestRequest();
        child.setName("Test child");
        child.setIsChild(true);
        CreateInviteGroupRequest request = new CreateInviteGroupRequest();
        request.setGroupName("  Test family  ");
        request.setGuests(List.of(adult, child));
        return request;
    }
}
