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
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(
    name = "students",
    indexes = {
        @Index(name = "idx_student_roll_no", columnList = "roll_no", unique = true),
        @Index(name = "idx_student_email", columnList = "email"),
        @Index(name = "idx_student_dept_active", columnList = "department_id, active"),
        @Index(name = "idx_student_course_active", columnList = "course_id, active"),
        @Index(name = "idx_student_class_section_active", columnList = "department_id, course_id, `year`, section, active")
    }
)
public class Student {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @NotBlank
    @Column(name = "roll_no", nullable = false, unique = true, length = 64)
    private String rollNo;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Email
    @Column(name = "email", nullable = false, length = 128)
    private String email;

    @Column(name = "phone", length = 32)
    private String phone;

    @NotBlank
    @Column(name = "department_id", nullable = false, length = 64)
    private String departmentId;

    @NotBlank
    @Column(name = "course_id", nullable = false, length = 64)
    private String courseId;

    @NotNull
    @Enumerated(EnumType.STRING)
    @Column(name = "program_type", nullable = false, length = 10)
    private ProgramType programType;

    @Min(1)
    @Max(4)
    @Column(name = "`year`", nullable = false)
    private int year;

    @NotBlank
    @Column(name = "section", nullable = false, length = 16)
    private String section;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @PrePersist
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }

    public Student() {
    }

    public Student(String rollNo, String name, String email, String phone, String departmentId,
                   String courseId, ProgramType programType, int year, String section, boolean active) {
        this.rollNo = rollNo;
        this.name = name;
        this.email = email;
        this.phone = phone;
        this.departmentId = departmentId;
        this.courseId = courseId;
        this.programType = programType;
        this.year = year;
        this.section = section;
        this.active = active;
    }

    public String getId() {
        return id;
    }

    public void setId(String id) {
        this.id = id;
    }

    public String getRollNo() {
        return rollNo;
    }

    public void setRollNo(String rollNo) {
        this.rollNo = rollNo;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPhone() {
        return phone;
    }

    public void setPhone(String phone) {
        this.phone = phone;
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

    public ProgramType getProgramType() {
        return programType;
    }

    public void setProgramType(ProgramType programType) {
        this.programType = programType;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
