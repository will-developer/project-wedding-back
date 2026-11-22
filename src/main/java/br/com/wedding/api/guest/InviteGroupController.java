package br.com.wedding.api.guest;

import java.util.UUID;
import java.util.List;

import br.com.wedding.api.guest.dto.CreateInviteGroupRequest;
import br.com.wedding.api.guest.dto.InviteGroupResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/admin/events/{eventId}/invite-groups")
@RequiredArgsConstructor
public class InviteGroupController {

    private final InviteGroupService inviteGroupService;

    @GetMapping
    public ResponseEntity<List<InviteGroupResponse>> list(@PathVariable UUID eventId) {
        return ResponseEntity.ok(inviteGroupService.list(eventId));
    }

    @PostMapping
    public ResponseEntity<InviteGroupResponse> create(@PathVariable UUID eventId,
            @Valid @RequestBody CreateInviteGroupRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(inviteGroupService.create(eventId, request));
    }
}
