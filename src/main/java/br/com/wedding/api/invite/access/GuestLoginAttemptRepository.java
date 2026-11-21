package br.com.wedding.api.invite.access;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestLoginAttemptRepository extends JpaRepository<GuestLoginAttempt, String> {
}
