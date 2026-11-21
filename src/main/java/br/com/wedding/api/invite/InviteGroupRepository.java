package br.com.wedding.api.invite;

import java.util.UUID;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface InviteGroupRepository extends JpaRepository<InviteGroup, UUID> {

    boolean existsByInviteCode(String inviteCode);

    List<InviteGroup> findAllByEventIdOrderByGroupNameAscIdAsc(UUID eventId);
}
