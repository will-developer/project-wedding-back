package br.com.wedding.api.admin;

import static java.util.Arrays.fill;
import static java.util.Arrays.mismatch;

import java.io.Console;

import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

@Component
@Profile("admin-setup")
@RequiredArgsConstructor
public class AdminAccountSetup {

    private final AdminAccountCreationService creationService;
    private final AdminAccountRepository accounts;

    public void run(Console console) {
        if (accounts.count() > 0) {
            throw new IllegalStateException("An administrator already exists. No account was changed.");
        }

        console.printf("Create the single administrator in the configured database.%n");
        String login = console.readLine("Login: ");
        if (login == null) {
            throw new IllegalArgumentException("Account creation cancelled.");
        }
        char[] password = null;
        char[] confirmation = null;
        try {
            password = console.readPassword("Password (at least 15 characters): ");
            if (password == null) {
                throw new IllegalArgumentException("Account creation cancelled.");
            }
            confirmation = console.readPassword("Repeat password: ");
            if (confirmation == null) {
                throw new IllegalArgumentException("Account creation cancelled.");
            }
            if (mismatch(password, confirmation) != -1) {
                throw new IllegalArgumentException("Passwords do not match.");
            }
            creationService.create(login, new String(password));
            console.printf("Administrator created successfully.%n");
        } catch (DataIntegrityViolationException exception) {
            throw new IllegalStateException("Account creation was rejected by the database.", exception);
        } finally {
            // Clear the temporary password buffers, including on failure.
            if (password != null) {
                fill(password, '\0');
            }
            if (confirmation != null) {
                fill(confirmation, '\0');
            }
        }
    }
}
