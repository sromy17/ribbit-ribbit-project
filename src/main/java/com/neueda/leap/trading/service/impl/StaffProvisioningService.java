package com.neueda.leap.trading.service.impl;

import java.util.Locale;
import java.util.Set;

import com.neueda.leap.trading.api.StaffProvisionRequest;
import com.neueda.leap.trading.api.StaffRoleResponse;
import com.neueda.leap.trading.domain.AuditRecord;
import com.neueda.leap.trading.domain.Role;
import com.neueda.leap.trading.domain.User;
import com.neueda.leap.trading.repository.jpa.AuditRecordRepository;
import com.neueda.leap.trading.repository.jpa.RoleRepository;
import com.neueda.leap.trading.repository.jpa.SessionRepository;
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
    private final SessionRepository sessionRepository;
    private final AuditRecordRepository auditRecordRepository;

    public StaffProvisioningService(
        UserRepository userRepository,
        RoleRepository roleRepository,
        SessionRepository sessionRepository,
        AuditRecordRepository auditRecordRepository
    ) {
        this.userRepository = userRepository;
        this.roleRepository = roleRepository;
        this.sessionRepository = sessionRepository;
        this.auditRecordRepository = auditRecordRepository;
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
        sessionRepository.deleteByUserUserId(user.getUserId());
        audit("STAFF_ROLE_GRANTED", "USER", user.getUserId().toString(), "SUCCESS");
        return new StaffRoleResponse(user.getUsername(), roleName);
    }

    @Transactional
    public void revoke(String username, String roleName) {
        String normalizedRole = normalizeRole(roleName);
        User user = userRepository.findByUsername(username)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + username));
        user.getRoles().removeIf(role -> normalizedRole.equalsIgnoreCase(role.getRoleName()));
        userRepository.save(user);
        sessionRepository.deleteByUserUserId(user.getUserId());
        audit("STAFF_ROLE_REVOKED", "USER", user.getUserId().toString(), "SUCCESS");
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
}