package com.ams.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.ams.model.Course;

@Repository
public interface CourseRepository extends JpaRepository<Course, String>, JpaSpecificationExecutor<Course> {

    Optional<Course> findByCode(String code);

    Optional<Course> findByName(String name);

    boolean existsByCode(String code);

    boolean existsByCodeAndIdNot(String code, String id);

    boolean existsByDepartmentIdAndName(String departmentId, String name);

    boolean existsByDepartmentIdAndNameAndIdNot(String departmentId, String name, String id);

    List<Course> findByDepartmentIdAndActiveTrue(String departmentId);

    List<Course> findByActiveTrue();
}
