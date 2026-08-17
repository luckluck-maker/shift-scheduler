package com.shiftscheduler.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;

import java.time.Instant;
import java.time.LocalDate;

// One week. Deleting it deletes its shifts.
@Entity
@Table(name = "schedule")
public class Schedule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "week_start", nullable = false, unique = true)
    private LocalDate weekStart;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ScheduleStatus status = ScheduleStatus.COLLECTING;

    @Column(name = "last_changed_at", nullable = false)
    private Instant lastChangedAt = Instant.now();

    @Version
    @Column(nullable = false)
    private long version;

    // When the submission window shuts on its own. Null means it stays open
    // until the manager closes it by hand.
    @Column(name = "submission_closes_at")
    private Instant submissionClosesAt;


    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public LocalDate getWeekStart() {
        return weekStart;
    }

    public void setWeekStart(LocalDate weekStart) {
        this.weekStart = weekStart;
    }

    public ScheduleStatus getStatus() {
        return status;
    }

    public void setStatus(ScheduleStatus status) {
        this.status = status;
    }

    public long getVersion() {
        return version;
    }

    public Instant getSubmissionClosesAt() {
        return submissionClosesAt;
    }

    public void setSubmissionClosesAt(Instant submissionClosesAt) {
        this.submissionClosesAt = submissionClosesAt;
    }

    // Raises the version of the week.
    // Assignments and requirements are in other tables, so Hibernate won't
    // notice a change unless a field here is touched.
    public void touch() {
        this.lastChangedAt = Instant.now();
    }

}