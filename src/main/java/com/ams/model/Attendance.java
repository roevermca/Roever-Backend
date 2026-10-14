package com.ams.model;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(
    name = "attendance",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_student_date_period", columnNames = {"student_id", "date", "period"})
    },
    indexes = {
        @Index(name = "idx_att_date_period", columnList = "date, period"),
        @Index(name = "idx_att_student_date", columnList = "student_id, date"),
        @Index(name = "idx_att_date", columnList = "date"),
        @Index(name = "idx_att_student", columnList = "student_id")
    }
)
public class Attendance {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @NotBlank
    @Column(name = "student_id", nullable = false, length = 64)
    private String studentId;

    @NotNull
    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Min(1)
    @Max(5)
    @Column(name = "period", nullable = false)
    private int period;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private AttendanceStatus status;

    @NotBlank
    @Column(name = "marked_by", nullable = false, length = 128)
    private String markedBy;

    @NotNull
    @Column(name = "marked_at", nullable = false)
    private Instant markedAt;

    @PrePersist
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }

    public Attendance() {
    }

    public Attendance(String studentId, LocalDate date, int period, AttendanceStatus status, String markedBy, Instant markedAt) {
        this.studentId = studentId;
        this.date = date;
        this.period = period;
        this.status = status;
        this.markedBy = markedBy;
        this.markedAt = markedAt;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getStudentId() {
        return studentId;
    }

    public void setStudentId(String studentId) {
        this.studentId = studentId;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public int getPeriod() {
        return period;
    }

    public void setPeriod(int period) {
        this.period = period;
    }

    public AttendanceStatus getStatus() {
        return status;
    }

    public void setStatus(AttendanceStatus status) {
        this.status = status;
    }

    public String getMarkedBy() {
        return markedBy;
    }

    public void setMarkedBy(String markedBy) {
        this.markedBy = markedBy;
    }

    public Instant getMarkedAt() {
        return markedAt;
    }

    public void setMarkedAt(Instant markedAt) {
        this.markedAt = markedAt;
    }
}
