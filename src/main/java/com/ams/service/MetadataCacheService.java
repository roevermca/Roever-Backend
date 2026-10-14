package com.ams.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.ams.model.Course;
import com.ams.model.Department;
import com.ams.repository.CourseRepository;
import com.ams.repository.DepartmentRepository;

import jakarta.annotation.PostConstruct;

/**
 * High-speed L1 in-memory metadata cache for Departments and Courses.
 * Eliminates repetitive database queries and table scans, providing O(1) in-memory resolution.
 */
@Service
public class MetadataCacheService {

    private static final Logger logger = LoggerFactory.getLogger(MetadataCacheService.class);

    private final DepartmentRepository departmentRepository;
    private final CourseRepository courseRepository;

    private final Map<String, Department> deptById = new ConcurrentHashMap<>();
    private final Map<String, Department> deptByCode = new ConcurrentHashMap<>();
    private final Map<String, Department> deptByName = new ConcurrentHashMap<>();

    private final Map<String, Course> courseById = new ConcurrentHashMap<>();
    private final Map<String, Course> courseByCode = new ConcurrentHashMap<>();
    private final Map<String, Course> courseByName = new ConcurrentHashMap<>();

    private final Map<String, List<String>> deptIdentifiersCache = new ConcurrentHashMap<>();
    private final Map<String, List<String>> courseIdentifiersCache = new ConcurrentHashMap<>();

    public MetadataCacheService(DepartmentRepository departmentRepository, CourseRepository courseRepository) {
        this.departmentRepository = departmentRepository;
        this.courseRepository = courseRepository;
    }

    @PostConstruct
    public synchronized void reload() {
        try {
            deptById.clear();
            deptByCode.clear();
            deptByName.clear();
            deptIdentifiersCache.clear();

            courseById.clear();
            courseByCode.clear();
            courseByName.clear();
            courseIdentifiersCache.clear();

            List<Department> depts = departmentRepository.findAll();
            for (Department d : depts) {
                if (d != null) {
                    if (d.getId() != null) deptById.put(d.getId(), d);
                    if (d.getCode() != null) deptByCode.put(d.getCode().toLowerCase().trim(), d);
                    if (d.getName() != null) deptByName.put(d.getName().toLowerCase().trim(), d);
                }
            }

            List<Course> courses = courseRepository.findAll();
            for (Course c : courses) {
                if (c != null) {
                    if (c.getId() != null) courseById.put(c.getId(), c);
                    if (c.getCode() != null) courseByCode.put(c.getCode().toLowerCase().trim(), c);
                    if (c.getName() != null) courseByName.put(c.getName().toLowerCase().trim(), c);
                }
            }

            logger.info("MetadataCache initialized with {} departments and {} courses (0ms in-memory access enabled).",
                    deptById.size(), courseById.size());
        } catch (Exception e) {
            logger.warn("MetadataCache warm-up deferred: {}", e.getMessage());
        }
    }

    public synchronized void evictDepartment(String id) {
        reload();
    }

    public synchronized void evictCourse(String id) {
        reload();
    }

    public String resolveDepartmentName(String identifier) {
        if (identifier == null || identifier.isBlank()) return "Computer Applications";
        String trimmed = identifier.trim();
        String lower = trimmed.toLowerCase();

        Department d = deptById.get(trimmed);
        if (d != null) return d.getName();

        d = deptByName.get(lower);
        if (d != null) return d.getName();

        d = deptByCode.get(lower);
        if (d != null) return d.getName();

        return trimmed;
    }

    public String resolveCourseName(String identifier) {
        if (identifier == null || identifier.isBlank()) return "";
        String trimmed = identifier.trim();
        String lower = trimmed.toLowerCase();

        Course c = courseById.get(trimmed);
        if (c != null) return c.getName();

        c = courseByName.get(lower);
        if (c != null) return c.getName();

        c = courseByCode.get(lower);
        if (c != null) return c.getName();

        return trimmed;
    }

    public List<String> getMatchingDeptIdentifiers(String identifier) {
        if (identifier == null || identifier.isBlank()) return List.of();
        String trimmed = identifier.trim();
        String lower = trimmed.toLowerCase();

        return deptIdentifiersCache.computeIfAbsent(trimmed, k -> {
            List<String> ids = new ArrayList<>();
            ids.add(trimmed);

            Department d = deptById.get(trimmed);
            if (d == null) d = deptByCode.get(lower);
            if (d == null) d = deptByName.get(lower);

            if (d != null) {
                if (d.getId() != null) ids.add(d.getId());
                if (d.getCode() != null) ids.add(d.getCode());
                if (d.getName() != null) ids.add(d.getName());
            }
            return ids.stream().distinct().toList();
        });
    }

    public List<String> getMatchingCourseIdentifiers(String identifier) {
        if (identifier == null || identifier.isBlank()) return List.of();
        String trimmed = identifier.trim();
        String lower = trimmed.toLowerCase();

        return courseIdentifiersCache.computeIfAbsent(trimmed, k -> {
            List<String> ids = new ArrayList<>();
            ids.add(trimmed);

            Course c = courseById.get(trimmed);
            if (c == null) c = courseByCode.get(lower);
            if (c == null) c = courseByName.get(lower);

            if (c != null) {
                if (c.getId() != null) ids.add(c.getId());
                if (c.getCode() != null) ids.add(c.getCode());
                if (c.getName() != null) ids.add(c.getName());
            }
            return ids.stream().distinct().toList();
        });
    }

    public List<Department> getAllActiveDepartments() {
        return deptById.values().stream().filter(Department::isActive).toList();
    }

    public List<Course> getAllActiveCourses() {
        return courseById.values().stream().filter(Course::isActive).toList();
    }
}
