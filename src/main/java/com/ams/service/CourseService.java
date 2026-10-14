package com.ams.service;

import java.util.List;

import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ams.dto.CourseRequest;
import com.ams.dto.CourseResponse;
import com.ams.exception.DuplicateResourceException;
import com.ams.exception.ResourceNotFoundException;
import com.ams.model.Course;
import com.ams.model.ProgramType;
import com.ams.repository.CourseRepository;
import com.ams.security.SecurityUtils;
import com.ams.specification.CourseSpecification;

@Service
public class CourseService {

    private final CourseRepository courseRepository;
    private final MetadataCacheService metadataCacheService;

    public CourseService(CourseRepository courseRepository, MetadataCacheService metadataCacheService) {
        this.courseRepository = courseRepository;
        this.metadataCacheService = metadataCacheService;
    }

    public List<CourseResponse> getCourses(
            String departmentId,
            ProgramType programType,
            Integer durationYears,
            Boolean active) {

        Specification<Course> spec = CourseSpecification.filter(
                departmentId,
                programType,
                durationYears,
                active
        );

        List<Course> courses = courseRepository.findAll(spec, Sort.by("name").ascending());
        return courses.stream()
                .map(CourseResponse::fromEntity)
                .toList();
    }

    public CourseResponse getCourseById(String id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + id));
        return CourseResponse.fromEntity(course);
    }

    @Transactional
    public CourseResponse createCourse(CourseRequest request) {
        SecurityUtils.enforceCourseManagementAccess(request.getDepartmentId());

        String code = request.getCode().trim().toUpperCase();
        if (courseRepository.existsByCode(code)) {
            throw new DuplicateResourceException("Course with code " + code + " already exists");
        }

        String name = request.getName().trim();
        String deptId = request.getDepartmentId().trim();
        if (courseRepository.existsByDepartmentIdAndName(deptId, name)) {
            throw new DuplicateResourceException("Course with name " + name + " already exists in this department");
        }

        Course course = new Course(
                name,
                code,
                deptId,
                request.getProgramType(),
                request.getDurationYears(),
                request.getActive() != null ? request.getActive() : true
        );

        Course saved = courseRepository.save(course);
        if (metadataCacheService != null) {
            metadataCacheService.evictCourse(saved.getId());
        }
        return CourseResponse.fromEntity(saved);
    }

    @Transactional
    public CourseResponse updateCourse(String id, CourseRequest request) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + id));

        SecurityUtils.enforceCourseManagementAccess(course.getDepartmentId());
        SecurityUtils.enforceCourseManagementAccess(request.getDepartmentId());

        String newCode = request.getCode().trim().toUpperCase();
        if (!course.getCode().equalsIgnoreCase(newCode) && courseRepository.existsByCode(newCode)) {
            throw new DuplicateResourceException("Course with code " + newCode + " already exists");
        }

        String newName = request.getName().trim();
        String deptId = request.getDepartmentId().trim();
        if (courseRepository.existsByDepartmentIdAndNameAndIdNot(deptId, newName, id)) {
            throw new DuplicateResourceException("Course with name " + newName + " already exists in this department");
        }

        course.setName(newName);
        course.setCode(newCode);
        course.setDepartmentId(deptId);
        course.setProgramType(request.getProgramType());
        course.setDurationYears(request.getDurationYears());
        if (request.getActive() != null) {
            course.setActive(request.getActive());
        }

        Course updated = courseRepository.save(course);
        if (metadataCacheService != null) {
            metadataCacheService.evictCourse(id);
        }
        return CourseResponse.fromEntity(updated);
    }

    @Transactional
    public void deleteCourse(String id) {
        Course course = courseRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Course not found with ID: " + id));
        SecurityUtils.enforceCourseManagementAccess(course.getDepartmentId());
        courseRepository.delete(course);
        if (metadataCacheService != null) {
            metadataCacheService.evictCourse(id);
        }
    }
}
