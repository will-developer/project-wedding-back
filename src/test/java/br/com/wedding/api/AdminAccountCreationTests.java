package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;

import br.com.wedding.api.admin.AdminAccount;
import br.com.wedding.api.admin.AdminAccountCreationService;
import br.com.wedding.api.admin.AdminAccountRepository;
import br.com.wedding.api.common.ActivationStatus;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.NONE,
        properties = "spring.flyway.enabled=true")
@Transactional
class AdminAccountCreationTests {

    @Autowired
    private AdminAccountCreationService creationService;

    @Autowired
    private AdminAccountRepository accounts;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Test
    void createsActiveAccountWithNormalizedLoginAndVerifiableHash() {
        String password = "local test password only";
        creationService.create("  William  ", password);

        AdminAccount account = accounts.findByLogin("william").orElseThrow();
        assertThat(account.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
        assertThat(account.getPasswordHash()).isNotEqualTo(password);
        assertThat(passwordEncoder.matches(password, account.getPasswordHash())).isTrue();
        assertThat(passwordEncoder.matches("wrong password", account.getPasswordHash())).isFalse();
    }

    @Test
    void refusesToReplaceInactiveAdministrator() {
        AdminAccount original = new AdminAccount("original", "test-hash-placeholder", Instant.now());
        original.deactivate(Instant.now());
        accounts.saveAndFlush(original);

        assertThatThrownBy(() -> creationService.create("another", "local test password only"))
                .isInstanceOf(IllegalStateException.class);
        assertThat(accounts.count()).isEqualTo(1);
        assertThat(accounts.findByLogin("original").orElseThrow().getPasswordHash())
                .isEqualTo("test-hash-placeholder");
    }

    @Test
    void rejectsShortPasswordWithoutSavingAccount() {
        assertThatThrownBy(() -> creationService.create("william", "short"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(accounts.count()).isZero();
    }

    @Test
    void rejectsPasswordAboveBcryptByteLimitWithoutSavingAccount() {
        // 37 characters, but 74 bytes when encoded as UTF-8.
        assertThatThrownBy(() -> creationService.create("william", "é".repeat(37)))
                .isInstanceOf(IllegalArgumentException.class);
        assertThat(accounts.count()).isZero();
    }
}
