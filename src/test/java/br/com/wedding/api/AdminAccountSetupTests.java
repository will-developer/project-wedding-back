package br.com.wedding.api;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.io.Console;

import br.com.wedding.api.admin.AdminAccountCreationService;
import br.com.wedding.api.admin.AdminAccountRepository;
import br.com.wedding.api.admin.AdminAccountSetup;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;

class AdminAccountSetupTests {

    @Test
    void rejectsMismatchedConfirmationAndClearsPasswordBuffers() {
        Console console = mock(Console.class);
        AdminAccountCreationService service = mock(AdminAccountCreationService.class);
        AdminAccountRepository accounts = mock(AdminAccountRepository.class);
        char[] password = "local test password only".toCharArray();
        char[] confirmation = "different test password".toCharArray();
        when(console.readLine("Login: ")).thenReturn("william");
        when(console.readPassword("Password (at least 15 characters): ")).thenReturn(password);
        when(console.readPassword("Repeat password: ")).thenReturn(confirmation);

        assertThatThrownBy(() -> new AdminAccountSetup(service, accounts).run(console))
                .isInstanceOf(IllegalArgumentException.class);
        verifyNoInteractions(service);
        assertThat(password).containsOnly('\0');
        assertThat(confirmation).containsOnly('\0');
    }

    @Test
    void cancellationStopsBeforeReadingPasswords() {
        Console console = mock(Console.class);
        AdminAccountCreationService service = mock(AdminAccountCreationService.class);
        AdminAccountRepository accounts = mock(AdminAccountRepository.class);

        assertThatThrownBy(() -> new AdminAccountSetup(service, accounts).run(console))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Account creation cancelled.");
        verify(console, never()).readPassword("Password (at least 15 characters): ");
        verifyNoInteractions(service);
    }

    @Test
    void databaseFailureKeepsCauseAndClearsPasswordBuffers() {
        Console console = mock(Console.class);
        AdminAccountCreationService service = mock(AdminAccountCreationService.class);
        AdminAccountRepository accounts = mock(AdminAccountRepository.class);
        char[] password = "local test password only".toCharArray();
        char[] confirmation = password.clone();
        when(console.readLine("Login: ")).thenReturn("william");
        when(console.readPassword("Password (at least 15 characters): ")).thenReturn(password);
        when(console.readPassword("Repeat password: ")).thenReturn(confirmation);
        DataIntegrityViolationException cause = new DataIntegrityViolationException("test constraint");
        doThrow(cause).when(service).create("william", "local test password only");

        assertThatThrownBy(() -> new AdminAccountSetup(service, accounts).run(console))
                .isInstanceOf(IllegalStateException.class)
                .hasCause(cause);
        verify(console, never()).printf("Administrator created successfully.%n");
        assertThat(password).containsOnly('\0');
        assertThat(confirmation).containsOnly('\0');
    }

    @Test
    void passesConfirmedCredentialsToServiceAndClearsPasswordBuffers() {
        Console console = mock(Console.class);
        AdminAccountCreationService service = mock(AdminAccountCreationService.class);
        AdminAccountRepository accounts = mock(AdminAccountRepository.class);
        char[] password = "local test password only".toCharArray();
        char[] confirmation = password.clone();
        when(console.readLine("Login: ")).thenReturn("william");
        when(console.readPassword("Password (at least 15 characters): ")).thenReturn(password);
        when(console.readPassword("Repeat password: ")).thenReturn(confirmation);

        new AdminAccountSetup(service, accounts).run(console);

        verify(service).create("william", "local test password only");
        assertThat(password).containsOnly('\0');
        assertThat(confirmation).containsOnly('\0');
    }
}
