package br.com.wedding.api.guest;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, UUID> {

    List<Guest> findAllByGroupEventIdOrderByNameAscIdAsc(UUID eventId);

    List<Guest> findAllByGroupIdOrderByNameAscIdAsc(UUID groupId);
}
