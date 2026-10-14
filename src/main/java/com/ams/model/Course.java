package com.ams.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(
    name = "courses",
    indexes = {
        @Index(name = "idx_course_code", columnList = "code", unique = true),
        @Index(name = "idx_course_dept_active", columnList = "department_id, active"),
        @Index(name = "idx_course_active", columnList = "active")
    }
)
public class Course {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @NotBlank
    @Column(name = "department_id", nullable = false, length = 64)
    private String departmentId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "program_type", nullable = false, length = 10)
    private ProgramType programType;

    @Min(1)
    @Column(name = "duration_years", nullable = false)
    private int durationYears;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @PrePersist
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }

    public Course() {
    }

    public Course(String name, String code, String departmentId, ProgramType programType, int durationYears, boolean active) {
        this.name = name;
        this.code = code;
        this.departmentId = departmentId;
        this.programType = programType;
        this.durationYears = durationYears;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getCode() {
        return code;
    }

    public void setCode(String code) {
        this.code = code;
    }

    public String getDepartmentId() {
        return departmentId;
    }

    public void setDepartmentId(String departmentId) {
        this.departmentId = departmentId;
    }

    public ProgramType getProgramType() {
        return programType;
    }

    public void setProgramType(ProgramType programType) {
        this.programType = programType;
    }

    public int getDurationYears() {
        return durationYears;
    }

    public void setDurationYears(int durationYears) {
        this.durationYears = durationYears;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
