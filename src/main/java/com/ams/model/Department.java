package com.ams.model;

import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;

@Entity
@Table(
    name = "departments",
    indexes = {
        @Index(name = "idx_dept_code", columnList = "code", unique = true),
        @Index(name = "idx_dept_active", columnList = "active")
    }
)
public class Department {

    @Id
    @Column(name = "id", length = 64, nullable = false, updatable = false)
    private String id;

    @NotBlank
    @Column(name = "name", nullable = false)
    private String name;

    @NotBlank
    @Column(name = "code", nullable = false, unique = true, length = 64)
    private String code;

    @Column(name = "active", nullable = false)
    private boolean active = true;

    @PrePersist
    public void ensureId() {
        if (this.id == null || this.id.isBlank()) {
            this.id = UUID.randomUUID().toString();
        }
    }

    public Department() {
    }

    public Department(String name, String code, boolean active) {
        this.name = name;
        this.code = code;
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

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }
}
