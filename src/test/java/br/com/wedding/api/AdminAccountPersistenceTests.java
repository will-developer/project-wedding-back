package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.UUID;

import br.com.wedding.api.admin.AdminAccount;
import br.com.wedding.api.admin.AdminAccountRepository;
import br.com.wedding.api.common.ActivationStatus;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

@Import(TestcontainersConfiguration.class)
@SpringBootTest(properties = "spring.flyway.enabled=true")
@Transactional
class AdminAccountPersistenceTests {

    private static final Instant CREATED_AT = Instant.parse("2026-11-18T12:00:00Z");

    @Autowired
    private AdminAccountRepository accounts;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private JdbcTemplate jdbc;

    @Test
    void persistsAndFindsAccountByLogin() {
        // Placeholder used only to test persistence, never a usable login credential.
        AdminAccount account = new AdminAccount("william", "test-hash-placeholder", CREATED_AT);
        accounts.saveAndFlush(account);
        entityManager.clear();

        AdminAccount loaded = accounts.findByLogin("william").orElseThrow();
        assertThat(loaded.getId()).isEqualTo(account.getId());
        assertThat(loaded.getPasswordHash()).isEqualTo("test-hash-placeholder");
        assertThat(loaded.getCreatedAt()).isEqualTo(CREATED_AT);
        assertThat(loaded.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
        assertThat(loaded.getDeactivatedAt()).isNull();
        assertThat(accounts.findByLogin("unknown")).isEmpty();
    }

    @Test
    void persistsDeactivationAndReactivation() {
        AdminAccount account = accounts.saveAndFlush(
                new AdminAccount("william", "test-hash-placeholder", CREATED_AT));
        Instant deactivatedAt = CREATED_AT.plusSeconds(60);
        account.deactivate(deactivatedAt);
        account.deactivate(deactivatedAt.plusSeconds(60));
        accounts.flush();
        entityManager.clear();

        AdminAccount inactive = accounts.findById(account.getId()).orElseThrow();
        assertThat(inactive.getStatus()).isEqualTo(ActivationStatus.INACTIVE);
        assertThat(inactive.getDeactivatedAt()).isEqualTo(deactivatedAt);

        inactive.reactivate();
        accounts.flush();
        entityManager.clear();

        AdminAccount active = accounts.findById(account.getId()).orElseThrow();
        assertThat(active.getStatus()).isEqualTo(ActivationStatus.ACTIVE);
        assertThat(active.getDeactivatedAt()).isNull();
    }

    @Test
    void databaseRejectsSecondAdministratorEvenIfFirstIsInactive() {
        AdminAccount first = new AdminAccount("first", "test-hash-placeholder", CREATED_AT);
        first.deactivate(CREATED_AT.plusSeconds(60));
        accounts.saveAndFlush(first);

        assertThatThrownBy(() -> accounts.saveAndFlush(
                new AdminAccount("second", "test-hash-placeholder", CREATED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("admin_account_singleton");
    }

    @Test
    void databaseRejectsInactiveAccountWithoutDeactivationDate() {
        assertThatThrownBy(() -> insertInvalidStatus("INACTIVE", null))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("admin_account_status_date_check");
    }

    @Test
    void databaseRejectsActiveAccountWithDeactivationDate() {
        assertThatThrownBy(() -> insertInvalidStatus("ACTIVE", java.sql.Timestamp.from(CREATED_AT)))
                .isInstanceOf(DataIntegrityViolationException.class)
                .hasMessageContaining("admin_account_status_date_check");
    }

    private void insertInvalidStatus(String status, java.sql.Timestamp deactivatedAt) {
        jdbc.update("""
                INSERT INTO admin_account (id, login, password_hash, created_at, status, deactivated_at)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), "william", "test-hash-placeholder",
                java.sql.Timestamp.from(CREATED_AT), status, deactivatedAt);
    }
}
