package com.neueda.leap.trading.domain;

import java.time.Instant;
import jakarta.persistence.*;

@Entity
@Table(name = "dispute_case_events")
public class DisputeCaseEvent {
    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "event_id") private Long eventId;
    @ManyToOne(fetch = FetchType.LAZY) @JoinColumn(name = "case_id", nullable = false)
    private DisputeCase disputeCase;
    @Column(name = "event_type", nullable = false, length = 40) private String eventType;
    @Column(name = "actor_user_id", nullable = false) private Integer actorUserId;
    @Column(name = "note", length = 2000) private String note;
    @Column(name = "occurred_at_utc", nullable = false) private Instant occurredAtUtc;
    @Column(name = "case_version", nullable = false) private Long caseVersion;
    public void setDisputeCase(DisputeCase v) { disputeCase = v; }
    public void setEventType(String v) { eventType = v; }
    public void setActorUserId(Integer v) { actorUserId = v; }
    public void setNote(String v) { note = v; }
    public void setOccurredAtUtc(Instant v) { occurredAtUtc = v; }
    public void setCaseVersion(Long v) { caseVersion = v; }
}