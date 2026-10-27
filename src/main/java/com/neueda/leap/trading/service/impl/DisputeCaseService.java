package com.neueda.leap.trading.service.impl;

import java.time.Instant;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

import com.neueda.leap.trading.api.DisputeNoteRequest;
import com.neueda.leap.trading.api.DisputeSubmissionRequest;
import com.neueda.leap.trading.api.DisputeTransitionRequest;
import com.neueda.leap.trading.domain.DisputeCase;
import com.neueda.leap.trading.domain.DisputeCaseEvent;
import com.neueda.leap.trading.domain.DisputeCaseStatus;
import com.neueda.leap.trading.domain.Order;
import com.neueda.leap.trading.repository.jpa.DisputeCaseRepository;
import com.neueda.leap.trading.repository.jpa.OrderRepository;
import com.neueda.leap.trading.repository.jpa.CashMovementRepository;
import com.neueda.leap.trading.repository.jpa.TradingAccountRepository;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

@Service
public class DisputeCaseService {
    private final org.springframework.jdbc.core.JdbcTemplate db;
    private final DisputeCaseRepository caseRepository;
    private final OrderRepository orderRepository;
    private final CashMovementRepository cashMovementRepository;
    private final TradingAccountRepository accountRepository;

    public DisputeCaseService(DisputeCaseRepository caseRepository, OrderRepository orderRepository,
                              CashMovementRepository cashMovementRepository, TradingAccountRepository accountRepository,
                              @org.springframework.beans.factory.annotation.Qualifier("oltpJdbcTemplate") org.springframework.jdbc.core.JdbcTemplate db) {
        this.db = db;
        this.caseRepository = caseRepository;
        this.orderRepository = orderRepository;
        this.cashMovementRepository = cashMovementRepository;
        this.accountRepository = accountRepository;
    }

    @Transactional
    public DisputeCase submit(DisputeSubmissionRequest request) {
        int actor = actorId();
        String type = request.transactionType().trim().toUpperCase();
        String reference = request.transactionId().trim();
        String payloadHash = hash(type + "|" + reference + "|" + request.title().trim() + "|" + request.reason().trim() + "|" + request.feeRelated());
        var existing = caseRepository.findByCustomerUserIdAndIdempotencyKey(actor, request.idempotencyKey().trim());
        if (existing.isPresent()) {
            if (!payloadHash.equals(existing.get().getPayloadHash())) conflict("Idempotency key was reused with a different submission");
            return existing.get();
        }
        DisputeCase dispute = new DisputeCase();
        dispute.setTransactionType(type);
        dispute.setTransactionReference(reference);
        dispute.setTitle(request.title().trim());
        dispute.setFeeRelated(request.feeRelated());
        if ("TRADE".equals(type)) {
            int orderId = parseReference(reference);
            Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found"));
            if (!Integer.valueOf(actor).equals(order.getAccount().getUser().getUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Transaction is not owned by the current user");
            if (order.getTrades().isEmpty()) throw new IllegalArgumentException("Only recorded trades can be disputed");
            dispute.setOrderId(order.getOrderId());
            dispute.setAccountId(order.getAccount().getAccountId());
        } else if ("CASH".equals(type)) {
            long movementId = parseLongReference(reference);
            var movement = cashMovementRepository.findById(movementId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Cash movement not found"));
            var account = accountRepository.findById(movement.getAccountId()).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Account not found"));
            if (!Integer.valueOf(actor).equals(account.getUser().getUserId())) throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Cash movement is not owned by the current user");
            dispute.setCashMovementId(movement.getMovementId());
            dispute.setAccountId(account.getAccountId());
        } else throw new IllegalArgumentException("Transaction type must be TRADE or CASH");
        dispute.setCustomerUserId(actor);
        dispute.setReason(request.reason().trim());
        dispute.setIdempotencyKey(request.idempotencyKey().trim());
        dispute.setPayloadHash(payloadHash);
        dispute.setCreatedAtUtc(Instant.now());
        dispute.setUpdatedAtUtc(Instant.now());
        dispute = caseRepository.save(dispute);
        append(dispute, "SUBMITTED", request.reason());
        return dispute;
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> customerSummary(int accountId) {
        requireCustomerAccount(accountId);
        var result = new java.util.LinkedHashMap<String, Object>();
        for (var row : db.queryForList("""
            SELECT order_id, COUNT(*) AS total,
                SUM(CASE WHEN status='RESOLVED' THEN 1 ELSE 0 END) AS resolved,
                SUM(CASE WHEN status<>'RESOLVED' THEN 1 ELSE 0 END) AS active
            FROM dispute_cases WHERE account_id=? AND customer_user_id=? AND transaction_type='TRADE'
            GROUP BY order_id
            """, accountId, actorId())) {
            long active = ((Number)row.get("active")).longValue();
            result.put(String.valueOf(row.get("order_id")), java.util.Map.of("total",row.get("total"),
                "active",active,"resolved",row.get("resolved"),"status",active>0?"UNDER_REVIEW":"RESOLVED"));
        }
        return result;
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> customerCases(int accountId, int orderId, int page, int size) {
        requireCustomerAccount(accountId);
        if(page<0 || size<1 || size>100) throw new IllegalArgumentException("Invalid pagination");
        var order=orderRepository.findById(orderId)
            .filter(item->item.getAccount().getAccountId().equals(accountId))
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Trade not found for this account"));
        var result=caseRepository.findByAccountIdAndOrderIdAndCustomerUserId(accountId,order.getOrderId(),actorId(),
            org.springframework.data.domain.PageRequest.of(page,size,org.springframework.data.domain.Sort.by("createdAtUtc").descending().and(org.springframework.data.domain.Sort.by("caseId").descending())));
        return java.util.Map.of("items",result.getContent().stream().map(CustomerCase::from).toList(),
            "total",result.getTotalElements(),"page",page,"size",size,"asOf",Instant.now().toString());
    }

    private void requireCustomerAccount(int accountId) {
        var account=accountRepository.findById(accountId)
            .orElseThrow(()->new ResponseStatusException(HttpStatus.NOT_FOUND,"Account not found"));
        if(!Integer.valueOf(actorId()).equals(account.getUser().getUserId()))
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,"Account is not owned by the current user");
    }

    /** Customer-visible fields only: internal notes, staff identity and replay metadata stay private. */
    public record CustomerCase(Long caseId, Integer orderId, String title, String reason, boolean feeRelated,
        DisputeCaseStatus status, String resolutionReason, Instant createdAtUtc, Instant updatedAtUtc) {
        public static CustomerCase from(DisputeCase item) {
            return new CustomerCase(item.getCaseId(),item.getOrderId(),item.getTitle(),item.getReason(),
                item.isFeeRelated(),item.getStatus(),item.getResolutionReason(),item.getCreatedAtUtc(),item.getUpdatedAtUtc());
        }
    }

    @Transactional(readOnly = true)
    public java.util.Map<String, Object> page(String status, int page, int size) {
        if (page < 0 || size < 1 || size > 100) throw new IllegalArgumentException("Invalid pagination");
        var pageable = org.springframework.data.domain.PageRequest.of(page, size, org.springframework.data.domain.Sort.by("createdAtUtc").ascending().and(org.springframework.data.domain.Sort.by("caseId")));
        var result = status == null || status.isBlank() ? caseRepository.findAll(pageable)
            : caseRepository.findByStatus(DisputeCaseStatus.valueOf(status.toUpperCase()), pageable);
        return java.util.Map.of("items", result.getContent(), "total", result.getTotalElements(), "page", page, "size", size, "asOf", Instant.now().toString());
    }

    @Transactional(readOnly = true)
    public List<DisputeCase> list(String status) {
        if (status == null || status.isBlank()) return caseRepository.findAll();
        return caseRepository.findByStatusOrderByCreatedAtUtcAsc(DisputeCaseStatus.valueOf(status.toUpperCase()));
    }

    @Transactional(readOnly = true)
    public java.util.List<java.util.Map<String, Object>> events(Long id) {
        find(id);
        return db.queryForList("SELECT event_id, event_type, actor_user_id, note, occurred_at_utc, case_version FROM dispute_case_events WHERE case_id=? ORDER BY occurred_at_utc, event_id", id);
    }

    @Transactional(readOnly = true)
    public DisputeCase get(Long caseId) { return find(caseId); }

    @Transactional
    public DisputeCase claim(Long caseId, DisputeTransitionRequest request) {
        DisputeCase dispute = checked(caseId, request.expectedVersion());
        if (dispute.getStatus() != DisputeCaseStatus.OPEN) conflict("Case is not open");
        dispute.setAssignedStaffUserId(actorId());
        dispute.setStatus(DisputeCaseStatus.CLAIMED);
        touch(dispute); append(dispute, "CLAIMED", null);
        return dispute;
    }

    @Transactional
    public DisputeCase accept(Long caseId, DisputeTransitionRequest request) {
        DisputeCase dispute = assigned(caseId, request.expectedVersion());
        if (dispute.getStatus() != DisputeCaseStatus.CLAIMED) conflict("Case is not claimable");
        dispute.setStatus(DisputeCaseStatus.IN_PROGRESS);
        touch(dispute); append(dispute, "ACCEPTED", request.reason());
        return dispute;
    }

    @Transactional
    public DisputeCase note(Long caseId, DisputeNoteRequest request) {
        DisputeCase dispute = assigned(caseId, request.expectedVersion());
        if (dispute.getStatus() != DisputeCaseStatus.IN_PROGRESS) conflict("Case is not in progress");
        touch(dispute); append(dispute, "NOTE", request.note());
        return dispute;
    }

    @Transactional
    public DisputeCase resolve(Long caseId, DisputeTransitionRequest request) {
        DisputeCase dispute = assigned(caseId, request.expectedVersion());
        if (dispute.getStatus() != DisputeCaseStatus.IN_PROGRESS) conflict("Case is not in progress");
        if (request.reason() == null || request.reason().isBlank()) throw new IllegalArgumentException("Resolution reason is required");
        if (request.outcome() == null || request.outcome().isBlank()) throw new IllegalArgumentException("Resolution outcome is required");
        String outcome = request.outcome().trim().toUpperCase();
        if (!List.of("UPHELD", "NOT_UPHELD", "DUPLICATE_RELATED").contains(outcome)) throw new IllegalArgumentException("Invalid resolution outcome");
        dispute.setResolutionReason(outcome + ": " + request.reason().trim());
        dispute.setStatus(DisputeCaseStatus.RESOLVED);
        touch(dispute); append(dispute, "RESOLVED", request.reason());
        return dispute;
    }

    @Transactional
    public DisputeCase release(Long caseId, DisputeTransitionRequest request) {
        DisputeCase dispute = checked(caseId, request.expectedVersion());
        if (dispute.getStatus() == DisputeCaseStatus.RESOLVED) conflict("Resolved cases cannot be released");
        if (!Integer.valueOf(actorId()).equals(dispute.getAssignedStaffUserId()) && !hasSupervisorPermission()) {
            conflict("Only the assigned admin or supervisor may release this case");
        }
        if (request.reason() == null || request.reason().isBlank()) throw new IllegalArgumentException("Release reason is required");
        dispute.setAssignedStaffUserId(null);
        dispute.setStatus(DisputeCaseStatus.OPEN);
        touch(dispute); append(dispute, "RELEASED", request.reason());
        return dispute;
    }

    @Transactional
    public DisputeCase reassign(Long caseId, DisputeTransitionRequest request) {
        DisputeCase dispute = checked(caseId, request.expectedVersion());
        if (dispute.getStatus() == DisputeCaseStatus.RESOLVED) conflict("Resolved cases cannot be reassigned");
        if (request.newAssigneeUserId() == null || request.reason() == null || request.reason().isBlank()) throw new IllegalArgumentException("Assignee and reassignment reason are required");
        var eligible = db.queryForObject("SELECT count(*) FROM user_roles ur JOIN roles r ON r.roleid=ur.roleid WHERE ur.userid=? AND UPPER(r.rolename) IN ('PLATFORM_ADMINISTRATOR','PLATFORM_ADMIN')", Integer.class, request.newAssigneeUserId());
        if (eligible == null || eligible == 0) throw new IllegalArgumentException("Assignee must be an active admin");
        dispute.setAssignedStaffUserId(request.newAssigneeUserId());
        dispute.setStatus(DisputeCaseStatus.CLAIMED);
        touch(dispute); append(dispute, "REASSIGNED", request.reason());
        return dispute;
    }

    private DisputeCase assigned(Long id, Long version) {
        DisputeCase dispute = checked(id, version);
        if (!Integer.valueOf(actorId()).equals(dispute.getAssignedStaffUserId())) conflict("Only the assigned staff member may mutate this case");
        return dispute;
    }

    private DisputeCase checked(Long id, Long version) {
        DisputeCase dispute = find(id);
        if (!java.util.Objects.equals(dispute.getVersion(), version)) conflict("Case changed; reload before updating");
        return dispute;
    }

    private DisputeCase find(Long id) {
        return caseRepository.findById(id).orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Dispute case not found"));
    }

    private void touch(DisputeCase dispute) { dispute.setUpdatedAtUtc(Instant.now()); }

    private void append(DisputeCase dispute, String type, String note) {
        DisputeCaseEvent event = new DisputeCaseEvent();
        event.setDisputeCase(dispute); event.setEventType(type); event.setActorUserId(actorId());
        event.setNote(note); event.setOccurredAtUtc(Instant.now()); event.setCaseVersion(dispute.getVersion());
        dispute.getEvents().add(event);
    }

    private int actorId() {
        Object value = ((JwtAuthenticationToken) SecurityContextHolder.getContext().getAuthentication()).getToken().getClaim("uid");
        if (!(value instanceof Number number)) throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Missing actor identity");
        return number.intValue();
    }

    private void conflict(String message) { throw new ResponseStatusException(HttpStatus.CONFLICT, message); }

    private boolean hasSupervisorPermission() {
        return SecurityContextHolder.getContext().getAuthentication().getAuthorities().stream()
            .anyMatch(authority -> "PERM_DISPUTE_SUPERVISOR".equals(authority.getAuthority()));
    }

    private int parseReference(String reference) { try { return Integer.parseInt(reference); } catch (NumberFormatException ex) { throw new IllegalArgumentException("Transaction ID must be numeric"); } }
    private long parseLongReference(String reference) { try { return Long.parseLong(reference); } catch (NumberFormatException ex) { throw new IllegalArgumentException("Cash movement ID must be numeric"); } }
    private String hash(String value) { try { return java.util.HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception ex) { throw new IllegalStateException(ex); } }
}
