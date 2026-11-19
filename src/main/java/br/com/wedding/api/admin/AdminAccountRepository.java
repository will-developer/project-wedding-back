package br.com.wedding.api.admin;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface AdminAccountRepository extends JpaRepository<AdminAccount, UUID> {

    Optional<AdminAccount> findByLogin(String login);
}
