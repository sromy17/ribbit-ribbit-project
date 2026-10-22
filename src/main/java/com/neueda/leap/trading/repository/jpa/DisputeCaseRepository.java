package com.neueda.leap.trading.repository.jpa;

import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import com.neueda.leap.trading.domain.DisputeCase;
import com.neueda.leap.trading.domain.DisputeCaseStatus;

public interface DisputeCaseRepository extends JpaRepository<DisputeCase, Long> {
    org.springframework.data.domain.Page<DisputeCase> findByStatus(DisputeCaseStatus status, org.springframework.data.domain.Pageable pageable);
    Optional<DisputeCase> findByCustomerUserIdAndIdempotencyKey(Integer customerUserId, String idempotencyKey);
    java.util.List<DisputeCase> findByStatusOrderByCreatedAtUtcAsc(DisputeCaseStatus status);
    java.util.List<DisputeCase> findByAssignedStaffUserIdOrderByUpdatedAtUtcDesc(Integer userId);
}