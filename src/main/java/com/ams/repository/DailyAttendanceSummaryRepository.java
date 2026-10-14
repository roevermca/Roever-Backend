package com.ams.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.ams.dto.DepartmentAttendanceSummary;
import com.ams.dto.OverallAttendanceSummary;
import com.ams.model.DailyAttendanceSummary;

@Repository
public interface DailyAttendanceSummaryRepository extends JpaRepository<DailyAttendanceSummary, String> {

    @Query(value = "SELECT " +
            "d.department_id AS departmentId, " +
            "COALESCE(SUM(d.total_students), 0) AS totalStudents, " +
            "COALESCE(SUM(d.present_count), 0) AS presentCount, " +
            "COALESCE(SUM(d.absent_count), 0) AS absentCount, " +
            "COALESCE(SUM(d.present_count + d.absent_count), 0) AS totalCount " +
            "FROM daily_attendance_summary d " +
            "WHERE (:startDate IS NULL OR d.date >= :startDate) " +
            "  AND (:endDate IS NULL OR d.date <= :endDate) " +
            "  AND (:filterDept = false OR d.department_id IN (:deptIds)) " +
            "  AND (:filterCourse = false OR d.course_id IN (:courseIds)) " +
            "  AND (:year IS NULL OR d.year = :year) " +
            "  AND (:section IS NULL OR d.section = :section) " +
            "GROUP BY d.department_id", nativeQuery = true)
    List<DepartmentAttendanceSummary> getRollupDepartmentSummary(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("filterDept") boolean filterDept,
            @Param("deptIds") Collection<String> deptIds,
            @Param("filterCourse") boolean filterCourse,
            @Param("courseIds") Collection<String> courseIds,
            @Param("year") Integer year,
            @Param("section") String section
    );

    @Query(value = "SELECT " +
            "COALESCE(SUM(d.total_students), 0) AS totalStudents, " +
            "COALESCE(SUM(d.present_count + d.absent_count), 0) AS totalRecords, " +
            "COALESCE(SUM(d.present_count), 0) AS presentRecords, " +
            "COALESCE(SUM(d.absent_count), 0) AS absentRecords " +
            "FROM daily_attendance_summary d " +
            "WHERE (:startDate IS NULL OR d.date >= :startDate) " +
            "  AND (:endDate IS NULL OR d.date <= :endDate) " +
            "  AND (:filterDept = false OR d.department_id IN (:deptIds)) " +
            "  AND (:filterCourse = false OR d.course_id IN (:courseIds)) " +
            "  AND (:year IS NULL OR d.year = :year) " +
            "  AND (:section IS NULL OR d.section = :section)", nativeQuery = true)
    OverallAttendanceSummary getRollupOverallSummary(
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate,
            @Param("filterDept") boolean filterDept,
            @Param("deptIds") Collection<String> deptIds,
            @Param("filterCourse") boolean filterCourse,
            @Param("courseIds") Collection<String> courseIds,
            @Param("year") Integer year,
            @Param("section") String section
    );

    @Modifying
    @Query(value = "INSERT INTO daily_attendance_summary " +
            "(id, date, department_id, course_id, year, section, total_students, present_count, absent_count, on_duty_count, attendance_rate) " +
            "SELECT " +
            "  MD5(CONCAT(:date, '_', s.department_id, '_', s.course_id, '_', s.year, '_', s.section)) AS id, " +
            "  :date AS date, " +
            "  s.department_id, " +
            "  s.course_id, " +
            "  s.year, " +
            "  s.section, " +
            "  COUNT(DISTINCT s.id) AS total_students, " +
            "  COALESCE(SUM(CASE WHEN a.status IN ('PRESENT', 'ON_DUTY') THEN 1 ELSE 0 END), 0) AS present_count, " +
            "  COALESCE(SUM(CASE WHEN a.status = 'ABSENT' THEN 1 ELSE 0 END), 0) AS absent_count, " +
            "  COALESCE(SUM(CASE WHEN a.status = 'ON_DUTY' THEN 1 ELSE 0 END), 0) AS on_duty_count, " +
            "  ROUND((SUM(CASE WHEN a.status IN ('PRESENT', 'ON_DUTY') THEN 1 ELSE 0 END) * 100.0) / NULLIF(COUNT(a.id), 0), 2) AS attendance_rate " +
            "FROM students s " +
            "JOIN attendance a ON a.student_id = s.id AND a.date = :date " +
            "WHERE s.department_id = :deptId AND s.course_id = :courseId AND s.year = :year AND s.section = :section " +
            "GROUP BY s.department_id, s.course_id, s.year, s.section " +
            "ON DUPLICATE KEY UPDATE " +
            "  total_students = VALUES(total_students), " +
            "  present_count = VALUES(present_count), " +
            "  absent_count = VALUES(absent_count), " +
            "  on_duty_count = VALUES(on_duty_count), " +
            "  attendance_rate = VALUES(attendance_rate)", nativeQuery = true)
    int refreshClassDailySummary(
            @Param("date") LocalDate date,
            @Param("deptId") String deptId,
            @Param("courseId") String courseId,
            @Param("year") int year,
            @Param("section") String section
    );
}
