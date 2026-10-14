package com.ams.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import com.ams.model.AttendanceArchive;

@Repository
public interface AttendanceArchiveRepository extends JpaRepository<AttendanceArchive, String> {

    List<AttendanceArchive> findByStudentId(String studentId);

    Optional<AttendanceArchive> findByStudentIdAndDateRange(String studentId, String dateRange);

    @Modifying
    @Transactional
    long deleteByStudentId(String studentId);

    @Modifying
    @Transactional
    @Query("DELETE FROM AttendanceArchive a WHERE a.studentId IN :studentIds")
    long deleteByStudentIdIn(@Param("studentIds") Collection<String> studentIds);

    @Modifying
    @Transactional
    @Query("DELETE FROM AttendanceArchive a WHERE a.studentId NOT IN :validStudentIds")
    long deleteByStudentIdNotIn(@Param("validStudentIds") Collection<String> validStudentIds);
}
