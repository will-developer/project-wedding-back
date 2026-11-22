package br.com.wedding.api.guest;

import java.util.UUID;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteGroupRepository extends JpaRepository<InviteGroup, UUID> {

    boolean existsByInviteCode(String inviteCode);

    Optional<InviteGroup> findByInviteCode(String inviteCode);

    List<InviteGroup> findAllByEventIdOrderByGroupNameAscIdAsc(UUID eventId);
}
