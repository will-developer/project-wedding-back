package br.com.wedding.api.gift;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface GiftRepository extends JpaRepository<Gift, UUID> {

    List<Gift> findAllByEventIdOrderByNameAscIdAsc(UUID eventId);
}
