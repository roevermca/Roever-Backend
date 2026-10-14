package com.ams.service;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ams.dto.AttendanceResponse;
import com.ams.dto.AttendanceSummaryResponse;
import com.ams.dto.BulkAttendanceRequest;
import com.ams.dto.BulkAttendanceResponse;
import com.ams.dto.PageResponse;
import com.ams.dto.StudentAttendanceRecord;
import com.ams.exception.AccessDeniedException;
import com.ams.exception.DuplicateResourceException;
import com.ams.exception.ResourceNotFoundException;
import com.ams.model.Attendance;
import com.ams.model.AttendanceStatus;
import com.ams.model.ProgramType;
import com.ams.model.Role;
import com.ams.model.Student;
import com.ams.repository.AttendanceArchiveRepository;
import com.ams.repository.AttendanceRepository;
import com.ams.repository.CourseRepository;
import com.ams.repository.DailyAttendanceSummaryRepository;
import com.ams.repository.StudentRepository;
import com.ams.security.AuthenticatedUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private CourseRepository courseRepository;

    @Mock
    private AttendanceArchiveRepository archiveRepository;

    @Mock
    private MetadataCacheService metadataCacheService;

    @Mock
    private DailyAttendanceSummaryRepository dailyAttendanceSummaryRepository;

    @InjectMocks
    private AttendanceService attendanceService;

    private void setSecurityContext(String userId, Role role, String departmentId, String studentId) {
        AuthenticatedUser user = new AuthenticatedUser(
                userId,
                "firebase-" + userId,
                userId + "@amsportal.edu",
                "Test User",
                role,
                departmentId,
                studentId
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @BeforeEach
    void setUp() {
        SecurityContextHolder.clearContext();
    }

    @Test
    @DisplayName("Bulk attendance records batch successfully and counts present/absent accurately")
    void markAttendanceBulk_success() {
        setSecurityContext("staff-1", Role.STAFF, "dept-cs", null);

        Student s1 = new Student("2024CS101", "Student 1", "s1@ams.edu", "1234567890",
                "dept-cs", "course-cs", ProgramType.UG, 1, "A", true);
        s1.setId("std-1");
        Student s2 = new Student("2024CS102", "Student 2", "s2@ams.edu", "1234567890",
                "dept-cs", "course-cs", ProgramType.UG, 1, "A", true);
        s2.setId("std-2");

        given(studentRepository.findAllById(anyCollection())).willReturn(List.of(s1, s2));
        given(attendanceRepository.findByStudentIdInAndDateAndPeriod(anyCollection(), any(), eq(1)))
                .willReturn(List.of());

        BulkAttendanceRequest request = new BulkAttendanceRequest(
                LocalDate.of(2026, 10, 2),
                1,
                List.of(
                        new StudentAttendanceRecord("std-1", AttendanceStatus.PRESENT),
                        new StudentAttendanceRecord("std-2", AttendanceStatus.ABSENT)
                )
        );

        BulkAttendanceResponse response = attendanceService.markAttendanceBulk(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalMarked()).isEqualTo(2);
        assertThat(response.getPresentCount()).isEqualTo(1);
        assertThat(response.getAbsentCount()).isEqualTo(1);
        verify(attendanceRepository).saveAll(anyList());
    }

    @Test
    @DisplayName("STAFF can mark attendance for students across different courses in their own department")
    void markAttendanceBulk_staffAcrossCoursesInSameDepartment() {
        setSecurityContext("staff-ca", Role.STAFF, "dept-ca", null);

        Student sBca = new Student("2024BCA01", "BCA Student", "bca@ams.edu", "1234567890",
                "dept-ca", "course-bca", ProgramType.UG, 1, "A", true);
        sBca.setId("std-bca-01");

        Student sMca = new Student("2024MCA01", "MCA Student", "mca@ams.edu", "1234567890",
                "dept-ca", "course-mca", ProgramType.PG, 1, "A", true);
        sMca.setId("std-mca-01");

        given(studentRepository.findAllById(anyCollection())).willReturn(List.of(sBca, sMca));
        given(attendanceRepository.findByStudentIdInAndDateAndPeriod(anyCollection(), any(), eq(1)))
                .willReturn(List.of());

        BulkAttendanceRequest request = new BulkAttendanceRequest(
                LocalDate.of(2026, 10, 2),
                1,
                List.of(
                        new StudentAttendanceRecord("std-bca-01", AttendanceStatus.PRESENT),
                        new StudentAttendanceRecord("std-mca-01", AttendanceStatus.PRESENT)
                )
        );

        BulkAttendanceResponse response = attendanceService.markAttendanceBulk(request);
        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getTotalMarked()).isEqualTo(2);
    }

    @Test
    @DisplayName("STAFF cannot mark attendance for students in another department")
    void markAttendanceBulk_staffOtherDepartmentBlocked() {
        setSecurityContext("staff-ca", Role.STAFF, "dept-ca", null);

        Student sEe = new Student("2024EE01", "EE Student", "ee@ams.edu", "1234567890",
                "dept-ee", "course-ee", ProgramType.UG, 1, "A", true);
        sEe.setId("std-ee-01");

        given(studentRepository.findAllById(anyCollection())).willReturn(List.of(sEe));

        BulkAttendanceRequest request = new BulkAttendanceRequest(
                LocalDate.of(2026, 10, 2),
                1,
                List.of(new StudentAttendanceRecord("std-ee-01", AttendanceStatus.PRESENT))
        );

        assertThrows(AccessDeniedException.class, () -> attendanceService.markAttendanceBulk(request));
    }

    @Test
    @DisplayName("Bulk attendance with fullDay=true successfully records all 5 periods")
    void markAttendanceBulk_fullDay_success() {
        setSecurityContext("staff-1", Role.STAFF, "dept-cs", null);

        Student s1 = new Student("2024CS101", "Student 1", "s1@ams.edu", "1234567890",
                "dept-cs", "course-cs", ProgramType.UG, 1, "A", true);
        s1.setId("std-1");
        Student s2 = new Student("2024CS102", "Student 2", "s2@ams.edu", "1234567890",
                "dept-cs", "course-cs", ProgramType.UG, 1, "A", true);
        s2.setId("std-2");

        given(studentRepository.findAllById(anyCollection())).willReturn(List.of(s1, s2));
        for (int p = 1; p <= 5; p++) {
            given(attendanceRepository.findByStudentIdInAndDateAndPeriod(anyCollection(), any(), eq(p)))
                    .willReturn(List.of());
        }

        BulkAttendanceRequest request = new BulkAttendanceRequest(
                LocalDate.of(2026, 10, 2),
                null,
                true,
                List.of(
                        new StudentAttendanceRecord("std-1", AttendanceStatus.PRESENT),
                        new StudentAttendanceRecord("std-2", AttendanceStatus.ABSENT)
                )
        );

        BulkAttendanceResponse response = attendanceService.markAttendanceBulk(request);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getMessage()).contains("full day (Periods 1–5)");
        assertThat(response.getTotalMarked()).isEqualTo(10); // 2 students * 5 periods = 10
        assertThat(response.getPresentCount()).isEqualTo(1);
        assertThat(response.getAbsentCount()).isEqualTo(1);
        verify(attendanceRepository).saveAll(anyList());
    }


    @Test
    @DisplayName("Duplicate attendance prevention throws DuplicateResourceException")
    void markAttendanceBulk_duplicatePrevention() {
        setSecurityContext("admin-1", Role.ADMIN, "dept-admin", null);

        Student s1 = new Student("2024CS101", "Student 1", "s1@ams.edu", "1234567890",
                "dept-cs", "course-cs", ProgramType.UG, 1, "A", true);
        s1.setId("std-1");

        given(studentRepository.findAllById(anyCollection())).willReturn(List.of(s1));
        given(attendanceRepository.findByStudentIdInAndDateAndPeriod(anyCollection(), any(), eq(1)))
                .willReturn(List.of(new Attendance("std-1", LocalDate.now(), 1, AttendanceStatus.PRESENT, "admin", java.time.Instant.now())));

        BulkAttendanceRequest request = new BulkAttendanceRequest(
                LocalDate.of(2026, 10, 2),
                1,
                List.of(new StudentAttendanceRecord("std-1", AttendanceStatus.PRESENT))
        );

        assertThrows(DuplicateResourceException.class, () -> attendanceService.markAttendanceBulk(request));
    }

    @Test
    @DisplayName("Missing student ID in bulk attendance throws ResourceNotFoundException")
    void markAttendanceBulk_studentNotFound() {
        setSecurityContext("admin-1", Role.ADMIN, "dept-admin", null);

        given(studentRepository.findAllById(anyCollection())).willReturn(List.of());

        BulkAttendanceRequest request = new BulkAttendanceRequest(
                LocalDate.of(2026, 10, 2),
                1,
                List.of(new StudentAttendanceRecord("std-missing", AttendanceStatus.PRESENT))
        );

        assertThrows(ResourceNotFoundException.class, () -> attendanceService.markAttendanceBulk(request));
    }

    @Test
    @DisplayName("HOD cannot mark attendance for students outside their own department")
    void markAttendanceBulk_hodCrossDepartmentDenied() {
        setSecurityContext("hod-cs", Role.HOD, "dept-cs", null);

        Student sMath = new Student("2024MATH101", "Math Student", "m@ams.edu", "1234567890",
                "dept-math", "course-math", ProgramType.UG, 1, "A", true);
        sMath.setId("std-math");

        given(studentRepository.findAllById(anyCollection())).willReturn(List.of(sMath));

        BulkAttendanceRequest request = new BulkAttendanceRequest(
                LocalDate.of(2026, 10, 2),
                1,
                List.of(new StudentAttendanceRecord("std-math", AttendanceStatus.PRESENT))
        );

        assertThrows(AccessDeniedException.class, () -> attendanceService.markAttendanceBulk(request));
    }

    @Test
    @DisplayName("Student cannot view attendance of other students")
    void getStudentAttendance_crossStudentAccessDenied() {
        setSecurityContext("student-1", Role.STUDENT, "dept-cs", "std-1");

        Student s2 = new Student("2024CS102", "Other Student", "s2@ams.edu", "1234567890",
                "dept-cs", "course-cs", ProgramType.UG, 1, "A", true);
        s2.setId("std-2");

        given(studentRepository.findById("std-2")).willReturn(Optional.of(s2));

        assertThrows(AccessDeniedException.class, () -> attendanceService.getStudentAttendance("std-2", 0, 20));
    }

    @Test
    @DisplayName("Student attendance summary calculates correct percentage")
    void getStudentAttendanceSummary_calculation() {
        setSecurityContext("student-1", Role.STUDENT, "dept-cs", "std-1");

        Student s1 = new Student("2024CS101", "Student 1", "s1@ams.edu", "1234567890",
                "dept-cs", "course-cs", ProgramType.UG, 1, "A", true);
        s1.setId("std-1");

        given(studentRepository.findById("std-1")).willReturn(Optional.of(s1));
        given(attendanceRepository.countByStudentId("std-1")).willReturn(40L);
        given(attendanceRepository.countByStudentIdAndStatus("std-1", AttendanceStatus.PRESENT)).willReturn(30L);
        given(attendanceRepository.countByStudentIdAndStatus("std-1", AttendanceStatus.ON_DUTY)).willReturn(4L);
        given(attendanceRepository.countByStudentIdAndStatus("std-1", AttendanceStatus.ABSENT)).willReturn(6L);

        AttendanceSummaryResponse summary = attendanceService.getStudentAttendanceSummary("std-1");

        assertThat(summary.getTotalClasses()).isEqualTo(40L);
        assertThat(summary.getPresent()).isEqualTo(34L);
        assertThat(summary.getAbsent()).isEqualTo(6L);
        assertThat(summary.getPercentage()).isEqualTo(85.0);
    }

    @Test
    @DisplayName("Attendance history pagination returns correct metadata")
    void attendanceHistory_pagination() {
        setSecurityContext("admin-1", Role.ADMIN, "dept-admin", null);

        List<Attendance> mockRecords = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            mockRecords.add(new Attendance("std-" + i, LocalDate.of(2026, 10, 2), 1,
                    AttendanceStatus.PRESENT, "staff-1", java.time.Instant.now()));
        }

        given(attendanceRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .willReturn(new PageImpl<>(mockRecords, PageRequest.of(0, 20), 55L));

        PageResponse<AttendanceResponse> response = attendanceService.getAttendanceHistory(
                null, LocalDate.of(2026, 10, 2), null, null, 1, null, 0, 20
        );

        assertThat(response.getData()).hasSize(20);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(20);
        assertThat(response.getTotalElements()).isEqualTo(55L);
        assertThat(response.getTotalPages()).isEqualTo(3);
    }

    @Test
    @DisplayName("Attendance history with studentIds filter queries only target students")
    void attendanceHistory_withStudentIdsList() {
        setSecurityContext("admin-1", Role.ADMIN, "dept-admin", null);

        List<Attendance> mockRecords = List.of(
                new Attendance("std-1", LocalDate.of(2026, 10, 2), 1, AttendanceStatus.PRESENT, "staff-1", java.time.Instant.now()),
                new Attendance("std-2", LocalDate.of(2026, 10, 2), 1, AttendanceStatus.ABSENT, "staff-1", java.time.Instant.now())
        );

        given(attendanceRepository.findAll(any(Specification.class), any(PageRequest.class)))
                .willReturn(new PageImpl<>(mockRecords, PageRequest.of(0, 20), 2L));

        PageResponse<AttendanceResponse> response = attendanceService.getAttendanceHistory(
                null, List.of("std-1", "std-2"), LocalDate.of(2026, 10, 2), null, null, 1, null, 0, 20
        );

        assertThat(response.getData()).hasSize(2);
        assertThat(response.getTotalElements()).isEqualTo(2L);
    }
}
