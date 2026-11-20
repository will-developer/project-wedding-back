package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import br.com.wedding.api.admin.AdminAccount;
import br.com.wedding.api.admin.AdminAccountRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.authentication.dao.DaoAuthenticationProvider;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@Transactional
class AdminAuthenticationTests {

    private static final String PASSWORD = "test password for authentication";

    @Autowired
    private AdminAccountRepository accounts;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private DaoAuthenticationProvider provider;

    @BeforeEach
    void createTestAccount() {
        accounts.saveAndFlush(new AdminAccount("will", passwordEncoder.encode(PASSWORD), Instant.now()));
    }

    @Test
    void authenticatesNormalizedLoginWithAdminRole() {
        Authentication result = authenticate("  WILL  ", PASSWORD);

        assertThat(result.isAuthenticated()).isTrue();
        assertThat(result.getName()).isEqualTo("will");
        assertThat(result.getAuthorities()).extracting(GrantedAuthority::getAuthority)
                .containsExactlyInAnyOrder("ROLE_ADMIN", "FACTOR_PASSWORD");
    }

    @Test
    void rejectsWrongPassword() {
        assertThatThrownBy(() -> authenticate("will", "incorrect password"))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsUnknownLoginWithoutExposingAccountLookupFailure() {
        assertThatThrownBy(() -> authenticate("unknown", PASSWORD))
                .isInstanceOf(BadCredentialsException.class);
    }

    @Test
    void rejectsInactiveAccountEvenWithCorrectPassword() {
        AdminAccount account = accounts.findByLogin("will").orElseThrow();
        account.deactivate(Instant.now());
        accounts.flush();

        assertThatThrownBy(() -> authenticate("will", PASSWORD))
                .isInstanceOf(DisabledException.class);
    }

    private Authentication authenticate(String login, String password) {
        return provider.authenticate(UsernamePasswordAuthenticationToken.unauthenticated(login, password));
    }
}
