package com.ams.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.stereotype.Repository;

import com.ams.model.Student;

@Repository
public interface StudentRepository extends JpaRepository<Student, String>, JpaSpecificationExecutor<Student> {

    Optional<Student> findByRollNo(String rollNo);

    Optional<Student> findByEmail(String email);

    boolean existsByRollNo(String rollNo);

    boolean existsByEmail(String email);

    boolean existsByRollNoAndIdNot(String rollNo, String id);

    boolean existsByEmailAndIdNot(String email, String id);

    List<Student> findByDepartmentIdAndCourseIdAndYearAndSectionAndActiveTrue(
        String departmentId,
        String courseId,
        int year,
        String section
    );

    List<Student> findByDepartmentIdAndActiveTrue(String departmentId);

    List<Student> findByDepartmentIdInAndActiveTrue(Collection<String> departmentIds);

    List<Student> findByCourseIdInAndActiveTrue(Collection<String> courseIds);

    List<Student> findByRollNoIn(Collection<String> rollNos);

    long countByDepartmentIdAndActiveTrue(String departmentId);

    long countByActiveTrue();
}
