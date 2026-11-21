package br.com.wedding.api.invite;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, UUID> {
}
