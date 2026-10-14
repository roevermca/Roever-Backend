package com.ams.service;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.ams.dto.AttendanceResponse;
import com.ams.dto.AttendanceSummaryResponse;
import com.ams.dto.BulkAttendanceRequest;
import com.ams.dto.BulkAttendanceResponse;
import com.ams.dto.PageResponse;
import com.ams.dto.StudentAttendanceRecord;
import com.ams.dto.UpdateAttendanceRequest;
import com.ams.exception.AccessDeniedException;
import com.ams.exception.DuplicateResourceException;
import com.ams.exception.ResourceNotFoundException;
import com.ams.model.Attendance;
import com.ams.model.AttendanceStatus;
import com.ams.model.Course;
import com.ams.model.Department;
import com.ams.model.Role;
import com.ams.model.Student;
import com.ams.repository.AttendanceArchiveRepository;
import com.ams.repository.AttendanceRepository;
import com.ams.repository.CourseRepository;
import com.ams.repository.DepartmentRepository;
import com.ams.repository.StudentRepository;
import com.ams.security.AuthenticatedUser;
import com.ams.security.SecurityUtils;
import com.ams.specification.AttendanceSpecification;
import com.ams.specification.StudentSpecification;

@Service
public class AttendanceService {

    private static final Logger logger = LoggerFactory.getLogger(AttendanceService.class);

    private final AttendanceRepository attendanceRepository;
    private final StudentRepository studentRepository;
    private final CourseRepository courseRepository;
    private final DepartmentRepository departmentRepository;
    private final AttendanceArchiveRepository archiveRepository;
    private final MetadataCacheService metadataCacheService;

    public AttendanceService(
            AttendanceRepository attendanceRepository,
            StudentRepository studentRepository,
            CourseRepository courseRepository,
            DepartmentRepository departmentRepository,
            AttendanceArchiveRepository archiveRepository,
            MetadataCacheService metadataCacheService) {
        this.attendanceRepository = attendanceRepository;
        this.studentRepository = studentRepository;
        this.courseRepository = courseRepository;
        this.departmentRepository = departmentRepository;
        this.archiveRepository = archiveRepository;
        this.metadataCacheService = metadataCacheService;
    }

    @Transactional
    public BulkAttendanceResponse markAttendanceBulk(BulkAttendanceRequest request) {
        SecurityUtils.enforceAttendanceMarkAccess();

        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        String markedBy = currentUser != null ? currentUser.getEmail() : "SYSTEM";

        List<StudentAttendanceRecord> records = request.getRecords();
        Set<String> studentIds = records.stream()
                .map(StudentAttendanceRecord::getStudentId)
                .collect(Collectors.toSet());

        // 1. Verify all students exist
        List<Student> students = studentRepository.findAllById(studentIds);
        if (students.size() != studentIds.size()) {
            Set<String> foundIds = students.stream().map(Student::getId).collect(Collectors.toSet());
            Set<String> missingIds = new HashSet<>(studentIds);
            missingIds.removeAll(foundIds);
            throw new ResourceNotFoundException("Student not found for IDs: " + missingIds);
        }

        // 2. HOD restriction: all students must belong to HOD's department
        if (currentUser != null && currentUser.getRole() == Role.HOD) {
            String hodDept = currentUser.getDepartmentId();
            for (Student student : students) {
                if (!student.getDepartmentId().equals(hodDept)) {
                    throw new AccessDeniedException(
                            "Forbidden: HOD can only mark attendance for students in their own department (" + hodDept + ")");
                }
            }
        }

        // Staff restriction: all students must belong to Staff's assigned course!
        if (currentUser != null && currentUser.getRole() == Role.STAFF) {
            String staffCourse = currentUser.getCourseId();
            if (staffCourse != null && !staffCourse.isBlank()) {
                List<String> allowedCourses = getMatchingCourseIdentifiers(staffCourse);
                for (Student student : students) {
                    if (!allowedCourses.contains(student.getCourseId())) {
                        throw new AccessDeniedException(
                                "Forbidden: Staff can only mark attendance for students in their assigned course (" + staffCourse + ")");
                    }
                }
            }
        }

        boolean isFullDay = Boolean.TRUE.equals(request.getFullDay());
        List<Integer> periodsToMark;
        if (isFullDay) {
            periodsToMark = List.of(1, 2, 3, 4, 5);
        } else {
            if (request.getPeriod() == null || request.getPeriod() < 1 || request.getPeriod() > 5) {
                throw new IllegalArgumentException("Period must be between 1 and 5 when not marking full day");
            }
            periodsToMark = List.of(request.getPeriod());
        }

        // 3. Prevent duplicate attendance and enforce locked state for STAFF
        for (Integer p : periodsToMark) {
            List<Attendance> existingRecords = attendanceRepository.findByStudentIdInAndDateAndPeriod(
                    studentIds, request.getDate(), p
            );

            if (!existingRecords.isEmpty()) {
                if (currentUser != null && currentUser.getRole() == Role.STAFF) {
                    throw new DuplicateResourceException(
                            "Attendance has already been submitted and locked for period " + p +
                            " on " + request.getDate() + ". Staff cannot edit submitted attendance.");
                }
                throw new DuplicateResourceException(
                        "Attendance already exists for " + existingRecords.size() +
                        " student(s) on " + request.getDate() + " period " + p);
            }
        }

        // 4. Server-generated timestamp and batch creation
        Instant markedAt = Instant.now();
        List<Attendance> toInsert = new ArrayList<>(records.size() * periodsToMark.size());
        int presentCount = 0;
        int absentCount = 0;

        for (Integer p : periodsToMark) {
            for (StudentAttendanceRecord record : records) {
                if (p == 1) {
                    if (record.getStatus() == AttendanceStatus.PRESENT || record.getStatus() == AttendanceStatus.ON_DUTY) {
                        presentCount++;
                    } else {
                        absentCount++;
                    }
                }
                toInsert.add(new Attendance(
                        record.getStudentId(),
                        request.getDate(),
                        p,
                        record.getStatus(),
                        markedBy,
                        markedAt
                ));
            }
        }

        // High-speed batch insert into MySQL
        attendanceRepository.saveAll(toInsert);

        return new BulkAttendanceResponse(
                true,
                isFullDay
                        ? "Attendance successfully recorded for full day (Periods 1–5) on " + request.getDate()
                        : "Attendance successfully recorded for period " + request.getPeriod() + " on " + request.getDate(),
                request.getDate(),
                isFullDay ? 0 : request.getPeriod(),
                toInsert.size(),
                presentCount,
                absentCount
        );
    }

    public PageResponse<AttendanceResponse> getAttendanceHistory(
            String studentId,
            LocalDate date,
            LocalDate startDate,
            LocalDate endDate,
            Integer period,
            AttendanceStatus status,
            int page,
            int size) {
        return getAttendanceHistory(studentId, null, date, startDate, endDate, period, status, page, size);
    }

    public PageResponse<AttendanceResponse> getAttendanceHistory(
            String studentId,
            List<String> studentIds,
            LocalDate date,
            LocalDate startDate,
            LocalDate endDate,
            Integer period,
            AttendanceStatus status,
            int page,
            int size) {
        return getAttendanceHistory(studentId, studentIds, null, null, null, date, startDate, endDate, period, status, page, size);
    }

    public PageResponse<AttendanceResponse> getAttendanceHistory(
            String studentId,
            List<String> studentIds,
            String departmentId,
            Integer year,
            String section,
            LocalDate date,
            LocalDate startDate,
            LocalDate endDate,
            Integer period,
            AttendanceStatus status,
            int page,
            int size) {

        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        List<String> effectiveStudentIds = (studentIds != null) ? new ArrayList<>(studentIds) : null;

        // Role-based scoping
        if (currentUser != null) {
            if (currentUser.getRole() == Role.STUDENT) {
                // Students can only see their own attendance
                studentId = currentUser.getStudentId();
                if (studentId == null) {
                    return new PageResponse<>(List.of(), page, size, 0L, 0);
                }
            } else if (currentUser.getRole() == Role.HOD) {
                // HOD can only see attendance of students in their own department
                if (studentId != null) {
                    Student student = studentRepository.findById(studentId).orElse(null);
                    if (student == null || !student.getDepartmentId().equals(currentUser.getDepartmentId())) {
                        throw new AccessDeniedException("Forbidden: HOD can only view attendance for own department");
                    }
                } else {
                    List<String> deptStudentIds = studentRepository.findByDepartmentIdAndActiveTrue(currentUser.getDepartmentId())
                            .stream().map(Student::getId).toList();
                    if (effectiveStudentIds == null) {
                        effectiveStudentIds = new ArrayList<>(deptStudentIds);
                    } else {
                        effectiveStudentIds.retainAll(deptStudentIds);
                    }
                }
            } else if (currentUser.getRole() == Role.STAFF) {
                if (currentUser.getCourseId() != null && !currentUser.getCourseId().isBlank()) {
                    List<String> allowedCourses = getMatchingCourseIdentifiers(currentUser.getCourseId());
                    if (studentId != null && !studentId.isBlank()) {
                        Student student = studentRepository.findById(studentId.trim()).orElse(null);
                        if (student == null || student.getCourseId() == null || !allowedCourses.contains(student.getCourseId())) {
                            throw new AccessDeniedException("Forbidden: Staff can only view attendance for students in their assigned course (" + currentUser.getCourseId() + ")");
                        }
                    } else {
                        List<String> courseStudentIds = studentRepository.findByCourseIdInAndActiveTrue(allowedCourses).stream()
                                .map(Student::getId).toList();
                        if (effectiveStudentIds == null) {
                            effectiveStudentIds = new ArrayList<>(courseStudentIds);
                        } else {
                            effectiveStudentIds.retainAll(courseStudentIds);
                        }
                    }
                }
            }
        }

        // Department/Year/Section criteria filtering
        List<String> deptList = (departmentId != null && !departmentId.isBlank())
                ? getMatchingDeptIdentifiers(departmentId)
                : null;
        if (deptList != null || year != null || (section != null && !section.isBlank())) {
            Specification<Student> stuSpec = StudentSpecification.filter(
                    deptList, null, null, year, section, null, null, null
            );
            List<String> filterStudentIds = studentRepository.findAll(stuSpec).stream().map(Student::getId).toList();
            if (filterStudentIds.isEmpty()) {
                return new PageResponse<>(List.of(), Math.max(0, page), (size <= 0) ? 20 : Math.min(100, size), 0L, 0);
            }
            if (effectiveStudentIds == null) {
                effectiveStudentIds = new ArrayList<>(filterStudentIds);
            } else {
                effectiveStudentIds.retainAll(filterStudentIds);
            }
        }

        Specification<Attendance> spec = AttendanceSpecification.filter(
                studentId,
                effectiveStudentIds,
                date,
                startDate,
                endDate,
                period,
                status
        );

        int validatedPage = Math.max(0, page);
        int validatedSize = (size <= 0) ? 20 : Math.min(100, size);

        PageRequest pageRequest = PageRequest.of(
                validatedPage,
                validatedSize,
                Sort.by("date").descending().and(Sort.by("period").descending())
        );

        Page<Attendance> pagedResult = attendanceRepository.findAll(spec, pageRequest);
        List<AttendanceResponse> data = enrichAttendanceResponses(pagedResult.getContent());

        return new PageResponse<>(
                data,
                validatedPage,
                validatedSize,
                pagedResult.getTotalElements(),
                pagedResult.getTotalPages()
        );
    }

    public PageResponse<AttendanceResponse> getStudentAttendance(String studentId, int page, int size) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        if (currentUser != null && currentUser.getRole() == Role.STAFF) {
            if (currentUser.getCourseId() != null && !currentUser.getCourseId().isBlank()) {
                List<String> allowedCourses = getMatchingCourseIdentifiers(currentUser.getCourseId());
                if (student.getCourseId() == null || !allowedCourses.contains(student.getCourseId())) {
                    throw new AccessDeniedException("Forbidden: Staff can only access attendance of students in their assigned course (" + currentUser.getCourseId() + ")");
                }
            }
        }

        SecurityUtils.enforceStudentAttendanceReadAccess(student.getId(), student.getDepartmentId());

        int validatedPage = Math.max(0, page);
        int validatedSize = (size <= 0) ? 20 : Math.min(100, size);

        PageRequest pageRequest = PageRequest.of(
                validatedPage,
                validatedSize,
                Sort.by("date").descending().and(Sort.by("period").descending())
        );

        Page<Attendance> pagedResult = attendanceRepository.findByStudentId(studentId, pageRequest);
        List<AttendanceResponse> data = enrichAttendanceResponses(pagedResult.getContent());

        return new PageResponse<>(
                data,
                validatedPage,
                validatedSize,
                pagedResult.getTotalElements(),
                pagedResult.getTotalPages()
        );
    }

    public AttendanceSummaryResponse getStudentAttendanceSummary(String studentId) {
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResourceNotFoundException("Student not found with ID: " + studentId));

        SecurityUtils.enforceStudentAttendanceReadAccess(student.getId(), student.getDepartmentId());

        long totalClasses = attendanceRepository.countByStudentId(studentId);
        long present = attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.PRESENT)
                     + attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.ON_DUTY);
        long absent = attendanceRepository.countByStudentIdAndStatus(studentId, AttendanceStatus.ABSENT);

        double percentage = totalClasses > 0
                ? Math.round(((double) present / totalClasses) * 1000.0) / 10.0
                : 0.0;

        return new AttendanceSummaryResponse(totalClasses, present, absent, percentage);
    }

    @Transactional
    public AttendanceResponse updateAttendanceRecord(String id, UpdateAttendanceRequest request) {
        SecurityUtils.enforceAttendanceEditAccess();

        Attendance record = attendanceRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Attendance record not found with ID: " + id));

        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        String editor = currentUser != null ? currentUser.getEmail() : "ADMIN";

        record.setStatus(request.getStatus());
        record.setMarkedBy(editor + " (Corrected)");
        record.setMarkedAt(Instant.now());

        Attendance saved = attendanceRepository.save(record);
        return AttendanceResponse.fromEntity(saved);
    }

    public List<String> getMatchingDeptIdentifiers(String deptIdentifier) {
        return metadataCacheService.getMatchingDeptIdentifiers(deptIdentifier);
    }

    public List<String> getMatchingCourseIdentifiers(String courseIdentifier) {
        return metadataCacheService.getMatchingCourseIdentifiers(courseIdentifier);
    }

    @Transactional
    public long purgeOrphanAttendanceRecords() {
        List<String> validStudentIds = studentRepository.findAll().stream().map(Student::getId).toList();
        List<String> validRollNos = studentRepository.findAll().stream().map(Student::getRollNo).filter(r -> r != null && !r.isBlank()).toList();
        Set<String> allValidIds = new HashSet<>(validStudentIds);
        allValidIds.addAll(validRollNos);

        long count = 0;
        if (!allValidIds.isEmpty()) {
            count = attendanceRepository.deleteByStudentIdNotIn(allValidIds);
            if (archiveRepository != null) {
                archiveRepository.deleteByStudentIdNotIn(allValidIds);
            }
        }
        return count;
    }

    private List<AttendanceResponse> enrichAttendanceResponses(List<Attendance> attendanceList) {
        if (attendanceList == null || attendanceList.isEmpty()) {
            return List.of();
        }

        Set<String> studentIds = attendanceList.stream()
                .map(Attendance::getStudentId)
                .filter(id -> id != null && !id.isBlank())
                .collect(Collectors.toSet());

        Map<String, Student> studentMap = new HashMap<>();
        if (!studentIds.isEmpty() && studentRepository != null) {
            try {
                List<Student> studentsById = studentRepository.findAllById(studentIds);
                if (studentsById != null) {
                    for (Student s : studentsById) {
                        if (s != null) {
                            if (s.getId() != null) studentMap.put(s.getId(), s);
                            if (s.getRollNo() != null) studentMap.put(s.getRollNo(), s);
                        }
                    }
                }

                Set<String> missingIds = studentIds.stream()
                        .filter(id -> !studentMap.containsKey(id))
                        .collect(Collectors.toSet());
                if (!missingIds.isEmpty()) {
                    List<Student> studentsByRoll = studentRepository.findByRollNoIn(missingIds);
                    if (studentsByRoll != null) {
                        for (Student s : studentsByRoll) {
                            if (s != null) {
                                if (s.getId() != null) studentMap.put(s.getId(), s);
                                if (s.getRollNo() != null) studentMap.put(s.getRollNo(), s);
                            }
                        }
                    }
                }
            } catch (Exception e) {
                logger.warn("Could not enrich attendance records with student details: {}", e.getMessage());
            }
        }

        return attendanceList.stream().map(att -> {
            Student stu = studentMap.get(att.getStudentId());
            String name = (stu != null && stu.getName() != null) ? stu.getName() : null;
            String rollNo = (stu != null && stu.getRollNo() != null) ? stu.getRollNo() : att.getStudentId();
            Integer yr = (stu != null) ? stu.getYear() : null;
            String sec = (stu != null) ? stu.getSection() : null;

            String rawDept = (stu != null) ? stu.getDepartmentId() : null;
            String deptName = (rawDept != null) ? metadataCacheService.resolveDepartmentName(rawDept) : null;

            String rawCourse = (stu != null) ? stu.getCourseId() : null;
            String crsName = (rawCourse != null) ? metadataCacheService.resolveCourseName(rawCourse) : null;

            AttendanceResponse resp = new AttendanceResponse(
                    att.getId(),
                    att.getStudentId(),
                    att.getDate(),
                    att.getPeriod(),
                    att.getStatus(),
                    att.getMarkedBy(),
                    att.getMarkedAt(),
                    name,
                    rollNo,
                    deptName,
                    crsName,
                    yr,
                    sec
            );
            resp.setDepartmentName(deptName);
            resp.setCourseName(crsName);
            return resp;
        }).toList();
    }
}

