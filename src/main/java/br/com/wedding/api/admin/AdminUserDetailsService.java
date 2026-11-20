package br.com.wedding.api.admin;

import static java.util.Locale.ROOT;

import br.com.wedding.api.common.ActivationStatus;
import lombok.RequiredArgsConstructor;
import org.jspecify.annotations.NullMarked;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AdminUserDetailsService implements UserDetailsService {

    private final AdminAccountRepository accounts;

    @Override
    @NullMarked
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String username) {
        if (username.isBlank()) {
            throw new UsernameNotFoundException("Invalid credentials.");
        }

        String login = username.strip().toLowerCase(ROOT);
        AdminAccount account = accounts.findByLogin(login)
                .orElseThrow(() -> new UsernameNotFoundException("Invalid credentials."));

        return User.withUsername(account.getLogin())
                .password(account.getPasswordHash())
                .roles("ADMIN")
                .disabled(account.getStatus() != ActivationStatus.ACTIVE)
                .build();
    }
}
