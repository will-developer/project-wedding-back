package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Instant;

import br.com.wedding.api.admin.AdminAccount;
import br.com.wedding.api.admin.AdminAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import tools.jackson.databind.json.JsonMapper;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
        "spring.flyway.enabled=true", "server.servlet.session.cookie.secure=false"
})
class AdminSessionHttpTests {

    private static final String PASSWORD = "test password for http login";
    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER).build();
    private final JsonMapper json = new JsonMapper();

    @LocalServerPort
    private int port;

    @Autowired
    private AdminAccountRepository accounts;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private JdbcTemplate jdbc;

    private String cookie;

    @BeforeEach
    void prepareAccount() {
        jdbc.update("DELETE FROM spring_session_attributes");
        jdbc.update("DELETE FROM spring_session");
        accounts.deleteAll();
        accounts.saveAndFlush(new AdminAccount("will", passwordEncoder.encode(PASSWORD), Instant.now()));
        cookie = null;
    }

    @Test
    void loginPersistsSessionRotatesIdAndLogoutInvalidatesIt() throws Exception {
        assertThat(send("GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
        String token = csrf();
        String anonymousCookie = cookie;
        HttpResponse<String> loginResponse = login("WILL", PASSWORD, token);
        assertThat(loginResponse.statusCode()).isEqualTo(204);
        String sessionCookie = loginResponse.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .findFirst().orElseThrow();
        assertThat(sessionCookie).contains("Max-Age=604800", "HttpOnly", "SameSite=Lax");
        assertThat(cookie).isNotEqualTo(anonymousCookie);
        HttpResponse<String> me = send("GET", "/api/auth/me", null, null);
        assertThat(me.statusCode()).isEqualTo(200);
        assertThat(json.readTree(me.body()).path("login").asText()).isEqualTo("will");
        assertThat(me.body()).doesNotContain("password", "$2", "credentials");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session WHERE principal_name = 'will'",
                Long.class)).isEqualTo(1L);
        assertThat(jdbc.queryForObject("SELECT max_inactive_interval FROM spring_session WHERE principal_name = 'will'",
                Integer.class)).isEqualTo(604800);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM spring_session_attributes WHERE attribute_name = 'SPRING_SECURITY_CONTEXT'",
                Long.class)).isEqualTo(1L);

        // Login clears the previous CSRF token; obtain a new token for logout.
        assertThat(send("POST", "/api/auth/logout", "", token).statusCode()).isEqualTo(403);
        token = csrf();
        assertThat(send("POST", "/api/auth/logout", "", token).statusCode()).isEqualTo(204);
        assertThat(send("GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void loginAndLogoutRequireCsrfToken() throws Exception {
        assertThat(login("will", PASSWORD, null).statusCode()).isEqualTo(403);
        assertThat(login("will", PASSWORD, csrf()).statusCode()).isEqualTo(204);
        assertThat(send("POST", "/api/auth/logout", "", null).statusCode()).isEqualTo(403);
        assertThat(send("GET", "/api/auth/me", null, null).statusCode()).isEqualTo(200);
    }

    @Test
    void wrongPasswordAndInactiveAccountReturnGenericUnauthorized() throws Exception {
        assertThat(login("will", "incorrect password", csrf()).statusCode()).isEqualTo(401);
        AdminAccount account = accounts.findByLogin("will").orElseThrow();
        account.deactivate(Instant.now());
        accounts.saveAndFlush(account);
        assertThat(login("will", PASSWORD, csrf()).statusCode()).isEqualTo(401);
    }

    @Test
    void deactivationInvalidatesExistingSession() throws Exception {
        assertThat(login("will", PASSWORD, csrf()).statusCode()).isEqualTo(204);
        AdminAccount account = accounts.findByLogin("will").orElseThrow();
        account.deactivate(Instant.now());
        accounts.saveAndFlush(account);

        assertThat(send("GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
        account.reactivate();
        accounts.saveAndFlush(account);
        assertThat(send("GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
    }

    @Test
    void expiredSessionDoesNotAuthenticate() throws Exception {
        assertThat(login("will", PASSWORD, csrf()).statusCode()).isEqualTo(204);
        jdbc.update("UPDATE spring_session SET last_access_time = 0, expiry_time = 0");
        assertThat(send("GET", "/api/auth/me", null, null).statusCode()).isEqualTo(401);
    }

    private String csrf() throws Exception {
        HttpResponse<String> response = send("GET", "/api/auth/csrf", null, null);
        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.headers().firstValue("Cache-Control").orElse("")).contains("no-store");
        return json.readTree(response.body()).path("token").asText();
    }

    private HttpResponse<String> login(String login, String password, String token) throws Exception {
        String body = "username=" + URLEncoder.encode(login, StandardCharsets.UTF_8)
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8);
        return send("POST", "/api/auth/login", body, token);
    }

    private HttpResponse<String> send(String method, String path, String body, String token) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (cookie != null) {
            request.header("Cookie", cookie);
        }
        if (token != null) {
            request.header("X-CSRF-TOKEN", token);
        }
        if (body != null) {
            request.header("Content-Type", "application/x-www-form-urlencoded")
                    .method(method, HttpRequest.BodyPublishers.ofString(body));
        } else {
            request.method(method, HttpRequest.BodyPublishers.noBody());
        }
        HttpResponse<String> response = client.send(request.build(), HttpResponse.BodyHandlers.ofString());
        response.headers().allValues("Set-Cookie").stream()
                .filter(value -> value.startsWith("SESSION="))
                .findFirst().ifPresent(value -> cookie = value.split(";", 2)[0]);
        return response;
    }
}
