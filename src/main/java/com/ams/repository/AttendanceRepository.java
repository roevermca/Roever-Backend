package com.ams.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ams.model.Attendance;
import com.ams.model.AttendanceStatus;

@Repository
public interface AttendanceRepository extends JpaRepository<Attendance, String>, JpaSpecificationExecutor<Attendance> {

    Optional<Attendance> findByStudentIdAndDateAndPeriod(String studentId, LocalDate date, int period);

    boolean existsByStudentIdAndDateAndPeriod(String studentId, LocalDate date, int period);

    List<Attendance> findByDateAndPeriod(LocalDate date, int period);

    List<Attendance> findByStudentIdAndDateBetween(String studentId, LocalDate startDate, LocalDate endDate);

    List<Attendance> findByStudentId(String studentId);

    Page<Attendance> findByStudentId(String studentId, Pageable pageable);

    long countByStudentId(String studentId);

    long countByStudentIdAndStatus(String studentId, AttendanceStatus status);

    List<Attendance> findByStudentIdInAndDateAndPeriod(Collection<String> studentIds, LocalDate date, int period);

    @Modifying
    @Transactional
    long deleteByDateLessThanEqual(LocalDate date);

    Optional<Attendance> findFirstByOrderByDateAsc();

    Optional<Attendance> findFirstByOrderByDateDesc();

    long countByDateLessThanEqual(LocalDate date);

    List<Attendance> findByDateLessThanEqual(LocalDate date);

    @Modifying
    @Transactional
    long deleteByStudentId(String studentId);

    @Modifying
    @Transactional
    @Query("DELETE FROM Attendance a WHERE a.studentId IN :studentIds")
    int deleteByStudentIdIn(@Param("studentIds") Collection<String> studentIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM Attendance a WHERE a.studentId NOT IN :validStudentIds")
    int deleteByStudentIdNotIn(@Param("validStudentIds") Collection<String> validStudentIds);

    @Query(value = "SELECT " +
            "s.department_id AS departmentId, " +
            "COUNT(DISTINCT s.id) AS totalStudents, " +
            "COALESCE(SUM(CASE WHEN a.status IN ('PRESENT', 'ON_DUTY') THEN 1 ELSE 0 END), 0) AS presentCount, " +
            "COALESCE(SUM(CASE WHEN a.status = 'ABSENT' THEN 1 ELSE 0 END), 0) AS absentCount, " +
            "COALESCE(COUNT(a.id), 0) AS totalCount " +
            "FROM students s " +
            "LEFT JOIN attendance a ON a.student_id = s.id " +
            "  AND (:startDate IS NULL OR a.date >= :startDate) " +
            "  AND (:endDate IS NULL OR a.date <= :endDate) " +
            "WHERE s.active = true " +
            "  AND (:filterDept = false OR s.department_id IN (:deptIds)) " +
            "  AND (:filterCourse = false OR s.course_id IN (:courseIds)) " +
            "  AND (:programType IS NULL OR s.program_type = :programType) " +
            "  AND (:year IS NULL OR s.year = :year) " +
            "  AND (:section IS NULL OR s.section = :section) " +
            "GROUP BY s.department_id", nativeQuery = true)
    List<com.ams.dto.DepartmentAttendanceSummary> getDepartmentAttendanceSummary(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("filterDept") boolean filterDept,
            @Param("deptIds") Collection<String> deptIds,
            @Param("filterCourse") boolean filterCourse,
            @Param("courseIds") Collection<String> courseIds,
            @Param("programType") String programType,
            @Param("year") Integer year,
            @Param("section") String section
    );

    @Query(value = "SELECT " +
            "COUNT(DISTINCT s.id) AS totalStudents, " +
            "COALESCE(COUNT(a.id), 0) AS totalRecords, " +
            "COALESCE(SUM(CASE WHEN a.status IN ('PRESENT', 'ON_DUTY') THEN 1 ELSE 0 END), 0) AS presentRecords, " +
            "COALESCE(SUM(CASE WHEN a.status = 'ABSENT' THEN 1 ELSE 0 END), 0) AS absentRecords " +
            "FROM students s " +
            "LEFT JOIN attendance a ON a.student_id = s.id " +
            "  AND (:startDate IS NULL OR a.date >= :startDate) " +
            "  AND (:endDate IS NULL OR a.date <= :endDate) " +
            "WHERE s.active = true " +
            "  AND (:filterDept = false OR s.department_id IN (:deptIds)) " +
            "  AND (:filterCourse = false OR s.course_id IN (:courseIds)) " +
            "  AND (:programType IS NULL OR s.program_type = :programType) " +
            "  AND (:year IS NULL OR s.year = :year) " +
            "  AND (:section IS NULL OR s.section = :section)", nativeQuery = true)
    com.ams.dto.OverallAttendanceSummary getOverallAttendanceSummary(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("filterDept") boolean filterDept,
            @Param("deptIds") Collection<String> deptIds,
            @Param("filterCourse") boolean filterCourse,
            @Param("courseIds") Collection<String> courseIds,
            @Param("programType") String programType,
            @Param("year") Integer year,
            @Param("section") String section
    );

    @Query(value = "SELECT " +
            "s.id AS studentId, " +
            "s.roll_no AS rollNo, " +
            "s.name AS name, " +
            "s.department_id AS departmentId, " +
            "s.course_id AS courseId, " +
            "s.program_type AS programType, " +
            "s.year AS year, " +
            "s.section AS section, " +
            "COALESCE(COUNT(a.id), 0) AS totalPeriods, " +
            "COALESCE(SUM(CASE WHEN a.status IN ('PRESENT', 'ON_DUTY') THEN 1 ELSE 0 END), 0) AS presentPeriods, " +
            "COALESCE(SUM(CASE WHEN a.status = 'ABSENT' THEN 1 ELSE 0 END), 0) AS absentPeriods " +
            "FROM students s " +
            "LEFT JOIN attendance a ON a.student_id = s.id " +
            "  AND (:startDate IS NULL OR a.date >= :startDate) " +
            "  AND (:endDate IS NULL OR a.date <= :endDate) " +
            "WHERE s.active = true " +
            "  AND (:filterDept = false OR s.department_id IN (:deptIds)) " +
            "  AND (:filterCourse = false OR s.course_id IN (:courseIds)) " +
            "  AND (:programType IS NULL OR s.program_type = :programType) " +
            "  AND (:year IS NULL OR s.year = :year) " +
            "  AND (:section IS NULL OR s.section = :section) " +
            "GROUP BY s.id, s.roll_no, s.name, s.department_id, s.course_id, s.program_type, s.year, s.section", nativeQuery = true)
    List<com.ams.dto.StudentAttendanceSummary> getStudentAttendanceSummary(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("filterDept") boolean filterDept,
            @Param("deptIds") Collection<String> deptIds,
            @Param("filterCourse") boolean filterCourse,
            @Param("courseIds") Collection<String> courseIds,
            @Param("programType") String programType,
            @Param("year") Integer year,
            @Param("section") String section
    );
}
