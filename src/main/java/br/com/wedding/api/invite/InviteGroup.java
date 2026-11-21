package br.com.wedding.api.invite;

import static java.util.Objects.requireNonNull;

import java.time.Instant;
import java.util.UUID;

import br.com.wedding.api.event.Event;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "invite_group")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InviteGroup {

    @Id
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "event_id", nullable = false)
    private Event event;

    @Column(name = "group_name", nullable = false, length = 160)
    private String groupName;

    @Column(name = "invite_code", nullable = false, unique = true, length = 6)
    private String inviteCode;

    @Column(columnDefinition = "text")
    private String notes;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    public InviteGroup(Event event, String groupName, String inviteCode, String notes) {
        this.event = requireNonNull(event, "event is required");
        this.groupName = requireNonNull(groupName, "groupName is required");
        if (inviteCode == null || !inviteCode.matches("[A-Z0-9]{6}")) {
            throw new IllegalArgumentException("inviteCode must contain six uppercase letters or digits");
        }
        this.inviteCode = inviteCode;
        this.notes = notes;
        this.id = UUID.randomUUID();
        this.createdAt = Instant.now();
    }
}
