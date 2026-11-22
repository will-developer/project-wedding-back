package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.never;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Map;
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
import br.com.wedding.api.guest.access.TurnstileVerifier;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true", "server.servlet.session.cookie.secure=false",
        "app.guest-access.turnstile.site-key=test-site-key"
})
class GuestSessionHttpTests {

    private static final String ADMIN_PASSWORD = "test password for guest session tests";
    private final HttpClient client = HttpClient.newBuilder().followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = new JsonMapper();

    @LocalServerPort
    private int port;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private EventRepository events;
    @Autowired
    private InviteGroupRepository groups;
    @Autowired
    private GuestRepository guests;
    @Autowired
    private AdminAccountRepository accounts;
    @Autowired
    private PasswordEncoder passwords;
    @MockitoBean
    private TurnstileVerifier turnstile;

    private String cookie;
    private UUID eventId;
    private InviteGroup firstFamily;
    private InviteGroup secondFamily;

    @BeforeEach
    void prepareFamilies() {
        cleanData();
        when(turnstile.isConfigured()).thenReturn(true);
        when(turnstile.verify(any())).thenAnswer(invocation -> "test-token".equals(invocation.getArgument(0)));
        cookie = null;
        accounts.saveAndFlush(new AdminAccount("will", passwords.encode(ADMIN_PASSWORD), Instant.now()));
        Event event = events.saveAndFlush(Event.builder()
                .id(UUID.randomUUID()).title("Test wedding")
                .eventDate(Instant.parse("2027-11-20T18:00:00Z"))
                .ceremonyLocation("Ceremony venue").receptionLocation("Reception venue")
                .ceremonyMapsUrl("https://www.google.com/maps?q=ceremony")
                .receptionMapsUrl("https://www.google.com/maps?q=reception")
                .rsvpDeadline(LocalDate.of(2027, 10, 23)).createdAt(Instant.now()).build());
        eventId = event.getId();
        firstFamily = saveFamily(event, "Family Silva", "A1B2C3");
        secondFamily = saveFamily(event, "Family Souza", "D4E5F6");
        saveGuest(firstFamily, "Ana", false);
        saveGuest(firstFamily, "Pedro", true);
        saveGuest(secondFamily, "Nina private", false);
    }

    @AfterEach
    void cleanData() {
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
        jdbc.update("DELETE FROM guest");
        jdbc.update("DELETE FROM invite_group");
        jdbc.update("DELETE FROM event");
        jdbc.update("DELETE FROM admin_account");
        jdbc.update("DELETE FROM guest_login_attempt");
    }

    @Test
    void loginRotatesSessionAndCsrfAndLogoutInvalidatesAccess() throws Exception {
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(401);
        String beforeLogin = csrf();
        String anonymousCookie = cookie;
        assertThat(login("  a1b2c3  ", beforeLogin).statusCode()).isEqualTo(204);
        assertThat(cookie).isNotEqualTo(anonymousCookie);
        assertThat(jdbc.queryForObject("SELECT max_inactive_interval FROM spring_session WHERE principal_name = ?",
                Integer.class, firstFamily.getId().toString())).isEqualTo(1800);

        HttpResponse<String> invitation = send("GET", "/api/guest/invitation", null, null);
        assertThat(invitation.statusCode()).isEqualTo(200);
        assertThat(invitation.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        var body = json.readTree(invitation.body());
        var event = body.path("event");
        assertThat(event.path("title").asText()).isEqualTo("Test wedding");
        assertThat(event.path("eventDate").asText()).isEqualTo("2027-11-20T18:00:00Z");
        assertThat(event.path("ceremonyLocation").asText()).isEqualTo("Ceremony venue");
        assertThat(event.path("receptionLocation").asText()).isEqualTo("Reception venue");
        assertThat(event.path("ceremonyMapsUrl").asText()).isEqualTo("https://www.google.com/maps?q=ceremony");
        assertThat(event.path("receptionMapsUrl").asText()).isEqualTo("https://www.google.com/maps?q=reception");
        assertThat(event.path("rsvpDeadline").asText()).isEqualTo("2027-10-23");
        assertThat(event.size()).isEqualTo(7);
        assertThat(body.path("groupName").asText()).isEqualTo("Family Silva");
        assertThat(body.path("guests").size()).isEqualTo(2);
        assertThat(body.path("guests").get(0).path("name").asText()).isEqualTo("Ana");
        assertThat(body.path("guests").get(1).path("isChild").asBoolean()).isTrue();
        assertThat(body.path("guests").get(0).path("rsvpStatus").asText()).isEqualTo("pending");
        assertThat(invitation.body()).doesNotContain("notes", "inviteCode", "createdAt", "Admin-only note", "Nina private");
        assertThat(send("GET", "/api/admin/events/" + eventId + "/invite-groups", null, null).statusCode())
                .isEqualTo(403);
        assertThat(send("GET", "/api/auth/me", null, null).statusCode()).isEqualTo(403);

        assertThat(send("POST", "/api/auth/logout", "", beforeLogin).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/auth/logout", "", csrf()).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void loginRequiresCsrfAndRejectsUnknownAndMalformedCodesUniformly() throws Exception {
        assertThat(login("A1B2C3", null).statusCode()).isEqualTo(403);
        String token = csrf();
        HttpResponse<String> malformed = login("invalid", token);
        HttpResponse<String> unknown = login("ZZZZZZ", token);
        assertThat(malformed.statusCode()).isEqualTo(401);
        assertThat(unknown.statusCode()).isEqualTo(401);
        assertThat(malformed.body()).isEqualTo(unknown.body());
        assertThat(send("POST", "/api/guest/login", "{", token).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void sessionChoosesTheFamilyAndReloginReplacesIt() throws Exception {
        assertThat(login("A1B2C3", csrf()).statusCode()).isEqualTo(204);
        HttpResponse<String> first = send("GET", "/api/guest/invitation?groupId=" + secondFamily.getId(), null, null);
        assertThat(first.statusCode()).isEqualTo(200);
        assertThat(json.readTree(first.body()).path("groupName").asText()).isEqualTo("Family Silva");

        String previousCookie = cookie;
        assertThat(login("D4E5F6", csrf()).statusCode()).isEqualTo(204);
        String currentCookie = cookie;
        assertThat(currentCookie).isNotEqualTo(previousCookie);
        HttpResponse<String> second = send("GET", "/api/guest/invitation", null, null);
        assertThat(json.readTree(second.body()).path("groupName").asText()).isEqualTo("Family Souza");
        assertThat(second.body()).doesNotContain("Pedro");
        cookie = previousCookie;
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void guestAndAdminLoginsReplaceRolesAndRestoreTheirSessionTimeouts() throws Exception {
        assertThat(send("POST", "/api/auth/login", "username=will&password=" + ADMIN_PASSWORD.replace(" ", "+"), csrf())
                .statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(403);
        assertThat(login("A1B2C3", csrf()).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/auth/me", null, null).statusCode()).isEqualTo(403);
        assertThat(send("POST", "/api/auth/login", "username=will&password=" + ADMIN_PASSWORD.replace(" ", "+"), csrf())
                .statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT max_inactive_interval FROM spring_session WHERE principal_name = 'will'",
                Integer.class)).isEqualTo(604800);
    }

    @Test
    void expiredSessionAndDeletedFamilyDoNotGrantAccess() throws Exception {
        assertThat(login("A1B2C3", csrf()).statusCode()).isEqualTo(204);
        jdbc.update("UPDATE spring_session SET last_access_time = 0, expiry_time = 0");
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(401);
        assertThat(login("A1B2C3", csrf()).statusCode()).isEqualTo(204);
        jdbc.update("DELETE FROM guest WHERE group_id = ?", firstFamily.getId());
        jdbc.update("DELETE FROM invite_group WHERE id = ?", firstFamily.getId());
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void rateLimitSurvivesNewCookiesAndIgnoresUntrustedForwardedHeaders() throws Exception {
        String token = csrf();
        for (int attempt = 0; attempt < 10; attempt++) {
            assertThat(login("ZZZZZZ", token).statusCode()).isEqualTo(401);
        }
        cookie = null;
        token = csrf();
        HttpResponse<String> blocked = login("A1B2C3", token);
        assertThat(blocked.statusCode()).isEqualTo(429);
        assertThat(blocked.headers().firstValue("Retry-After")).contains("900");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM guest_login_attempt", Integer.class)).isEqualTo(1);
        jdbc.update("UPDATE guest_login_attempt SET window_started_at = ?",
                Timestamp.from(Instant.now().minusSeconds(901)));
        assertThat(login("A1B2C3", token).statusCode()).isEqualTo(204);
    }

    @Test
    void rejectsOversizedAndNonJsonLoginPayloads() throws Exception {
        String token = csrf();
        assertThat(send("POST", "/api/guest/login", "{\"code\":\"" + "A".repeat(5000) + "\"}", token).statusCode())
                .isEqualTo(400);
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + "/api/guest/login"))
                .header("Cookie", cookie).header("X-CSRF-TOKEN", token)
                .header("Content-Type", "text/plain")
                .POST(HttpRequest.BodyPublishers.ofString("A1B2C3")).build();
        assertThat(client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode()).isEqualTo(400);
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void unavailableCounterDoesNotBypassLoginProtection() throws Exception {
        String token = csrf();
        jdbc.execute("ALTER TABLE guest_login_attempt RENAME TO guest_login_attempt_unavailable");
        try {
            assertThat(login("A1B2C3", token).statusCode()).isEqualTo(503);
            assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(401);
        } finally {
            jdbc.execute("ALTER TABLE guest_login_attempt_unavailable RENAME TO guest_login_attempt");
        }
    }

    private InviteGroup saveFamily(Event event, String name, String code) {
        return groups.saveAndFlush(InviteGroup.builder().id(UUID.randomUUID()).event(event)
                .groupName(name).inviteCode(code).notes("Admin-only note").createdAt(Instant.now()).build());
    }

    private void saveGuest(InviteGroup group, String name, boolean child) {
        guests.saveAndFlush(Guest.builder().id(UUID.randomUUID()).group(group).name(name)
                .child(child).rsvpStatus(RsvpStatus.PENDING).build());
    }

    private String csrf() throws Exception {
        HttpResponse<String> response = send("GET", "/api/auth/csrf", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        return json.readTree(response.body()).path("token").asText();
    }

    private HttpResponse<String> login(String code, String token) throws Exception {
        return send("POST", "/api/guest/login",
                json.writeValueAsString(Map.of("code", code, "turnstileToken", "test-token")), token);
    }

    @Test
    void threeFailedCodesRequireTurnstileEvenWithANewCookie() throws Exception {
        String token = csrf();
        for (int failure = 1; failure <= 3; failure++) {
            HttpResponse<String> response = send("POST", "/api/guest/login", "{\"code\":\"ZZZZZZ\"}", token);
            assertThat(response.statusCode()).isEqualTo(401);
            assertThat(json.readTree(response.body()).path("turnstileRequired").asBoolean()).isEqualTo(failure == 3);
        }
        verify(turnstile, never()).verify(any());

        cookie = null;
        token = csrf();
        HttpResponse<String> missingChallenge = send("POST", "/api/guest/login", "{\"code\":\"A1B2C3\"}", token);
        assertThat(missingChallenge.statusCode()).isEqualTo(403);
        var body = json.readTree(missingChallenge.body());
        assertThat(body.path("code").asText()).isEqualTo("TURNSTILE_REQUIRED");
        assertThat(body.path("siteKey").asText()).isEqualTo("test-site-key");
        assertThat(body.path("action").asText()).isEqualTo("guest-login");
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(401);

        assertThat(send("POST", "/api/guest/login",
                "{\"code\":\"A1B2C3\",\"turnstileToken\":\"bad-token\"}", token).statusCode()).isEqualTo(403);
        assertThat(login("A1B2C3", token).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(200);
        assertThat(send("POST", "/api/guest/login", "{\"code\":\"A1B2C3\"}", csrf()).statusCode()).isEqualTo(403);
    }

    @Test
    void missingTurnstileConfigurationDoesNotBypassChallenge() throws Exception {
        when(turnstile.isConfigured()).thenReturn(false);
        String token = csrf();
        for (int failure = 1; failure <= 2; failure++) {
            assertThat(login("ZZZZZZ", token).statusCode()).isEqualTo(401);
        }
        HttpResponse<String> response = login("ZZZZZZ", token);
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo("TURNSTILE_UNAVAILABLE");
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void unavailableTurnstileDoesNotAuthenticateValidInvitation() throws Exception {
        String token = csrf();
        for (int failure = 1; failure <= 3; failure++) {
            assertThat(login("ZZZZZZ", token).statusCode()).isEqualTo(401);
        }
        when(turnstile.verify(any())).thenThrow(
                new org.springframework.security.authentication.AuthenticationServiceException("Unavailable"));
        HttpResponse<String> response = login("A1B2C3", token);
        assertThat(response.statusCode()).isEqualTo(503);
        assertThat(json.readTree(response.body()).path("code").asText()).isEqualTo("LOGIN_UNAVAILABLE");
        assertThat(send("GET", "/api/guest/invitation", null, null).statusCode()).isEqualTo(401);
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("X-Forwarded-For", UUID.randomUUID().toString());
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        if (token != null) {
            request.header("X-CSRF-TOKEN", token);
        }
        if (body != null) {
            request.header("Content-Type", path.equals("/api/auth/login")
                            ? "application/x-www-form-urlencoded" : "application/json")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        response.headers().allValues("Set-Cookie").stream().filter(value -> value.startsWith("SESSION="))
                .findFirst().ifPresent(value -> cookie = value.split(";", 2)[0]);
        return response;
    }
}
