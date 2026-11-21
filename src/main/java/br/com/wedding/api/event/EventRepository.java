package br.com.wedding.api.event;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

public interface EventRepository extends JpaRepository<Event, UUID> {
}
