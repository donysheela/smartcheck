package com.student.smartcheck.domain;

import jakarta.persistence.*;

import java.time.Instant;

@Entity
@Table(name = "tracking_sessions")
public class TrackingSession {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(optional = false, fetch = FetchType.LAZY)
    @JoinColumn(name = "student_id", nullable = false)
    private Student student;

    @Column(nullable = false)
    private Instant startedAt;

    @Column(nullable = false)
    private Instant endsAt;

    private Instant completedAt;

    private Integer addedMinutes = 0;

    @Column(nullable = false)
    private boolean active = true;

    @Column(nullable = false)
    private boolean tenMinuteReminderSent;

    @Column(nullable = false)
    private boolean finalAlertSent;

    @Column(nullable = false)
    private boolean alertsEnabled;

    protected TrackingSession() {
    }

    public TrackingSession(Student student, Instant startedAt, Instant endsAt, boolean alertsEnabled) {
        this.student = student;
        this.startedAt = startedAt;
        this.endsAt = endsAt;
        this.alertsEnabled = alertsEnabled;
    }

    public Long getId() { return id; }
    public Student getStudent() { return student; }
    public Instant getStartedAt() { return startedAt; }
    public Instant getEndsAt() { return endsAt; }
    public Instant getCompletedAt() { return completedAt; }
    public int getAddedMinutes() { return addedMinutes == null ? 0 : addedMinutes; }
    public boolean isActive() { return active; }
    public boolean isTenMinuteReminderSent() { return tenMinuteReminderSent; }
    public boolean isFinalAlertSent() { return finalAlertSent; }
    public boolean isAlertsEnabled() { return alertsEnabled; }

    public void markTenMinuteReminderSent() { tenMinuteReminderSent = true; }
    public void markFinalAlertSent(Instant completedAt) {
        finalAlertSent = true;
        complete(completedAt);
    }
    public void addMinutes(int minutes) {
        endsAt = endsAt.plusSeconds(minutes * 60L);
        addedMinutes = getAddedMinutes() + minutes;
    }
    public void complete(Instant completedAt) {
        if (active) {
            active = false;
            this.completedAt = completedAt;
        }
    }
}
