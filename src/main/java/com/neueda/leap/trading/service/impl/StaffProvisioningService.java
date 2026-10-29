package com.neueda.leap.trading.service.impl;

import java.util.Locale;
import java.util.Set;

import com.neueda.leap.trading.api.StaffProvisionRequest;
import com.neueda.leap.trading.api.StaffRoleResponse;
import com.neueda.leap.trading.domain.AuditRecord;
import com.neueda.leap.trading.domain.Role;
import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.domain.DisputeCaseEvent;
import com.neueda.leap.trading.domain.DisputeCaseStatus;
import com.neueda.leap.trading.repository.jpa.AuditRecordRepository;
import com.neueda.leap.trading.repository.jpa.DisputeCaseRepository;
import com.neueda.leap.trading.repository.jpa.RoleRepository;
import com.neueda.leap.trading.repository.jpa.UserRepository;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class StaffProvisioningService {
    private static final Set<String> STAFF_ROLES = Set.of("OPERATIONS", "COMPLIANCE", "ANALYST", "PLATFORM_ADMINISTRATOR");

    private final UserRepository userRepository;
    private final RoleRepository roleRepository;
    private final AuthenticationService authenticationService;
    private final AuditRecordRepository auditRecordRepository;
    private final DisputeCaseRepository disputeCaseRepository;

    public StaffProvisioningService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        AuthenticationService authenticationService,
        AuditRecordRepository auditRecordRepository,
        DisputeCaseRepository disputeCaseRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.authenticationService = authenticationService;
        this.auditRecordRepository = auditRecordRepository;
        this.disputeCaseRepository = disputeCaseRepository;
    }

    @Transactional
    public StaffRoleResponse grant(StaffProvisionRequest request) {
        String roleName = normalizeRole(request.role());
        User user = userRepository.findByUsername(request.username())
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + request.username()));
        Role role = roleRepository.findByRoleNameIgnoreCase(roleName)
            .orElseGet(() -> {
                Role created = new Role();
                created.setRoleName(roleName);
                return roleRepository.save(created);
            });
        user.getRoles().add(role);
        role.getUsers().add(user);
        userRepository.save(user);
        authenticationService.revokeUserSessions(user.getUserId());
        audit("STAFF_ROLE_GRANTED", "USER", user.getUserId().toString(), "SUCCESS");
        return new StaffRoleResponse(user.getUsername(), roleName);
    }

    @Transactional
    public void revoke(String username, String roleName) {
        String normalizedRole = normalizeRole(roleName);
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        var removed = user.getRoles().stream().filter(role -> normalizedRole.equalsIgnoreCase(role.getRoleName())).toList();
        removed.forEach(role -> { role.getUsers().removeIf(member -> member.getUserId().equals(user.getUserId())); roleRepository.save(role); });
        user.getRoles().removeAll(removed);
        userRepository.save(user);
        boolean stillAdmin = user.getRoles().stream().anyMatch(role ->
            java.util.Set.of("PLATFORM_ADMIN", "PLATFORM_ADMINISTRATOR").contains(role.getRoleName().toUpperCase(Locale.ROOT)));
        if (!stillAdmin) releaseAssignedCases(user.getUserId());
        authenticationService.revokeUserSessions(user.getUserId());
        audit("STAFF_ROLE_REVOKED", "USER", user.getUserId().toString(), "SUCCESS");
    }

    private void releaseAssignedCases(Integer userId) {
        disputeCaseRepository.findByAssignedStaffUserIdOrderByUpdatedAtUtcDesc(userId).stream()
            .filter(dispute -> dispute.getStatus() != DisputeCaseStatus.RESOLVED)
            .forEach(dispute -> {
                dispute.setAssignedStaffUserId(null);
                dispute.setStatus(DisputeCaseStatus.OPEN);
                dispute.setUpdatedAtUtc(java.time.Instant.now());
                DisputeCaseEvent event = new DisputeCaseEvent();
                event.setDisputeCase(dispute);
                event.setEventType("AUTO_RELEASED_ACCESS_REVOKED");
                event.setActorUserId(actorId());
                event.setNote("Assigned staff access revoked");
                event.setOccurredAtUtc(java.time.Instant.now());
                event.setCaseVersion(dispute.getVersion());
                dispute.getEvents().add(event);
                disputeCaseRepository.save(dispute);
            });
    }

    private String normalizeRole(String role) {
        String normalized = role == null ? "" : role.trim().toUpperCase(Locale.ROOT);
        if (!STAFF_ROLES.contains(normalized)) {
            throw new IllegalArgumentException("Unsupported staff role: " + role);
        }
        return normalized;
    }

    private void audit(String action, String targetType, String targetId, String outcome) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        AuditRecord record = new AuditRecord();
        if (authentication instanceof JwtAuthenticationToken jwtAuthentication) {
            record.setActorUserId(jwtAuthentication.getToken().getClaim("uid"));
            record.setActorUsername(jwtAuthentication.getName());
        } else {
            record.setActorUsername("system");
        }
        record.setAction(action);
        record.setTargetType(targetType);
        record.setTargetId(targetId);
        record.setOutcome(outcome);
        record.setOccurredAt(java.time.LocalDateTime.now());
        auditRecordRepository.save(record);
    }

    private Integer actorId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication instanceof JwtAuthenticationToken jwt) {
            Object value = jwt.getToken().getClaim("uid");
            if (value instanceof Number number) return number.intValue();
        }
        return 0;
    }
}
