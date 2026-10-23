package com.neueda.leap.trading.domain;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "client_segment_history", uniqueConstraints = @UniqueConstraint(
    name = "uk_client_segment_start", columnNames = {"client_id", "effective_from_utc"}))
public class ClientSegmentMembership {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "membership_id")
    private Long membershipId;
    @Column(name = "client_id", nullable = false)
    private Integer clientId;
    @Column(name = "segment_code", nullable = false, length = 60)
    private String segmentCode;
    @Column(name = "source", nullable = false, length = 200)
    private String source;
    @Column(name = "effective_from_utc", nullable = false)
    private Instant effectiveFromUtc;
    @Column(name = "effective_to_utc")
    private Instant effectiveToUtc;
    @Column(name = "recorded_at_utc", nullable = false)
    private Instant recordedAtUtc;
}
