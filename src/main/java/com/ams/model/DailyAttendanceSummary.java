package com.ams.model;

import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

@Entity
@Table(
    name = "daily_attendance_summary",
    uniqueConstraints = {
        @UniqueConstraint(name = "uk_daily_class_summary", columnNames = {"date", "department_id", "course_id", "year", "section"})
    }
)
public class DailyAttendanceSummary {

    @Id
    @Column(length = 64)
    private String id;

    @Column(name = "date", nullable = false)
    private LocalDate date;

    @Column(name = "department_id", length = 64, nullable = false)
    private String departmentId;

    @Column(name = "course_id", length = 64, nullable = false)
    private String courseId;

    @Column(name = "year", nullable = false)
    private int year;

    @Column(name = "section", length = 16, nullable = false)
    private String section;

    @Column(name = "total_students", nullable = false)
    private int totalStudents;

    @Column(name = "present_count", nullable = false)
    private int presentCount;

    @Column(name = "absent_count", nullable = false)
    private int absentCount;

    @Column(name = "on_duty_count", nullable = false)
    private int onDutyCount;

    @Column(name = "attendance_rate", nullable = false)
    private double attendanceRate;

    public DailyAttendanceSummary() {
    }

    public DailyAttendanceSummary(String id, LocalDate date, String departmentId, String courseId,
                                  int year, String section, int totalStudents, int presentCount,
                                  int absentCount, int onDutyCount, double attendanceRate) {
        this.id = id;
        this.date = date;
        this.departmentId = departmentId;
        this.courseId = courseId;
        this.year = year;
        this.section = section;
        this.totalStudents = totalStudents;
        this.presentCount = presentCount;
        this.absentCount = absentCount;
        this.onDutyCount = onDutyCount;
        this.attendanceRate = attendanceRate;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public LocalDate getDate() {
        return date;
    }

    public void setDate(LocalDate date) {
        this.date = date;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public String getCourseId() {
        return courseId;
    }

    public void setCourseId(String courseId) {
        this.courseId = courseId;
    }

    public int getYear() {
        return year;
    }

    public void setYear(int year) {
        this.year = year;
    }

    public String getSection() {
        return section;
    }

    public void setSection(String section) {
        this.section = section;
    }

    public int getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(int totalStudents) {
        this.totalStudents = totalStudents;
    }

    public int getPresentCount() {
        return presentCount;
    }

    public void setPresentCount(int presentCount) {
        this.presentCount = presentCount;
    }

    public int getAbsentCount() {
        return absentCount;
    }

    public void setAbsentCount(int absentCount) {
        this.absentCount = absentCount;
    }

    public int getOnDutyCount() {
        return onDutyCount;
    }

    public void setOnDutyCount(int onDutyCount) {
        this.onDutyCount = onDutyCount;
    }

    public double getAttendanceRate() {
        return attendanceRate;
    }

    public void setAttendanceRate(double attendanceRate) {
        this.attendanceRate = attendanceRate;
    }
}
