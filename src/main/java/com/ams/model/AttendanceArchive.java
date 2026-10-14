package com.ams.model;

import java.time.Instant;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "attendance_archives",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_archive_student_range", columnNames = {"student_id", "date_range"})
    },
    indexes = {
        @Index(name = "idx_arch_student", columnList = "student_id")
    }
)
public class AttendanceArchive {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @Column(name = "student_id", nullable = false, length = 64)
    private String studentId;

    @Column(name = "date_range", nullable = false, length = 128)
    private String dateRange;

    @Column(name = "total_records", nullable = false)
    private long totalRecords;

    @Column(name = "present_records", nullable = false)
    private long presentRecords;

    @Column(name = "absent_records", nullable = false)
    private long absentRecords;

    @Column(name = "attendance_percentage", nullable = false)
    private double attendancePercentage;

    @Column(name = "archived_at", nullable = false)
    private Instant archivedAt;

    @PrePersist
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }

    public AttendanceArchive() {
    }

    public AttendanceArchive(String studentId, String dateRange, long totalRecords,
                             long presentRecords, long absentRecords, double attendancePercentage,
                             Instant archivedAt) {
        this.studentId = studentId;
        this.dateRange = dateRange;
        this.totalRecords = totalRecords;
        this.presentRecords = presentRecords;
        this.absentRecords = absentRecords;
        this.attendancePercentage = attendancePercentage;
        this.archivedAt = archivedAt;
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

    public String getDateRange() {
        return dateRange;
    }

    public void setDateRange(String dateRange) {
        this.dateRange = dateRange;
    }

    public long getTotalRecords() {
        return totalRecords;
    }

    public void setTotalRecords(long totalRecords) {
        this.totalRecords = totalRecords;
    }

    public long getPresentRecords() {
        return presentRecords;
    }

    public void setPresentRecords(long presentRecords) {
        this.presentRecords = presentRecords;
    }

    public long getAbsentRecords() {
        return absentRecords;
    }

    public void setAbsentRecords(long absentRecords) {
        this.absentRecords = absentRecords;
    }

    public double getAttendancePercentage() {
        return attendancePercentage;
    }

    public void setAttendancePercentage(double attendancePercentage) {
        this.attendancePercentage = attendancePercentage;
    }

    public Instant getArchivedAt() {
        return archivedAt;
    }

    public void setArchivedAt(Instant archivedAt) {
        this.archivedAt = archivedAt;
    }
}
