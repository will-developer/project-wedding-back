package br.com.wedding.api.guest;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GuestRepository extends JpaRepository<Guest, UUID> {

    Optional<Guest> findByIdAndGroupIdAndGroupEventId(UUID id, UUID groupId, UUID eventId);

    List<Guest> findAllByGroupEventIdOrderByNameAscIdAsc(UUID eventId);

    List<Guest> findAllByGroupIdOrderByNameAscIdAsc(UUID groupId);
}
