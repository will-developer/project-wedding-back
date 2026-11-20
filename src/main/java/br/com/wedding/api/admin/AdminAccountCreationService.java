package br.com.wedding.api.admin;

import static java.nio.charset.StandardCharsets.UTF_8;
import static java.util.Locale.ROOT;

import java.time.Instant;

import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminAccountCreationService {

    private final AdminAccountRepository accounts;
    private final PasswordEncoder passwordEncoder;

    @Transactional
    public void create(String login, String password) {
        if (accounts.count() > 0) {
            throw new IllegalStateException("An administrator already exists, including inactive accounts.");
        }
        if (login == null || login.isBlank()) {
            throw new IllegalArgumentException("Login is required.");
        }
        String normalizedLogin = login.strip().toLowerCase(ROOT);
        if (normalizedLogin.length() > 160) {
            throw new IllegalArgumentException("Login must have at most 160 characters.");
        }
        if (password == null || password.isBlank() || password.codePointCount(0, password.length()) < 15) {
            throw new IllegalArgumentException("Password must have at least 15 characters.");
        }

        if (password.getBytes(UTF_8).length > 72) {
            throw new IllegalArgumentException("Password must have at most 72 UTF-8 bytes.");
        }

        String passwordHash = passwordEncoder.encode(password);
        AdminAccount account = new AdminAccount(normalizedLogin, passwordHash, Instant.now());
        accounts.saveAndFlush(account);
    }
}
