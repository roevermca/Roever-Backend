package com.ams.service;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import com.ams.dto.ClassAttendanceSummary;
import com.ams.dto.DashboardSummaryResponse;
import com.ams.dto.DepartmentAttendanceSummary;
import com.ams.dto.OverallAttendanceSummary;
import com.ams.dto.StudentAttendanceSummary;
import com.ams.model.Department;
import com.ams.model.Role;
import com.ams.repository.AttendanceRepository;
import com.ams.repository.DepartmentRepository;
import com.ams.repository.StudentRepository;
import com.ams.repository.UserRepository;
import com.ams.security.AuthenticatedUser;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

@ExtendWith(MockitoExtension.class)
class DashboardServiceTest {

    @Mock
    private StudentRepository studentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private AttendanceRepository attendanceRepository;

    @Mock
    private DepartmentRepository departmentRepository;

    @Mock
    private MetadataCacheService metadataCacheService;

    @InjectMocks
    private DashboardService dashboardService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

    private void setSecurityContext(Role role, String departmentId) {
        AuthenticatedUser user = new AuthenticatedUser(
                "test-uid",
                "fb-uid",
                "test@roever.edu.in",
                "Test Staff",
                role,
                departmentId,
                null
        );
        UsernamePasswordAuthenticationToken auth =
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities());
        SecurityContextHolder.getContext().setAuthentication(auth);
    }

    @Test
    @DisplayName("STAFF login scopes dashboard metrics exclusively to their department fallback when no classes")
    void staffDashboard_scopesToStaffDepartmentOnly() {
        String deptId = "26374668-1367-46cc-aae6-f53ecfd0bff3";
        setSecurityContext(Role.STAFF, deptId);

        List<String> matchingDeptIds = List.of(deptId, "CA", "Computer Applications");
        given(metadataCacheService.getMatchingDeptIdentifiers(deptId)).willReturn(matchingDeptIds);
        given(metadataCacheService.resolveDepartmentName(deptId)).willReturn("Computer Applications");

        given(studentRepository.countByDepartmentIdInAndActiveTrue(matchingDeptIds)).willReturn(50L);
        given(userRepository.countByDepartmentIdInAndRoleInAndActiveTrue(eq(matchingDeptIds), any())).willReturn(4L);

        OverallAttendanceSummary todaySummary = mock(OverallAttendanceSummary.class);
        given(todaySummary.getPresentRecords()).willReturn(10L);
        given(todaySummary.getAbsentRecords()).willReturn(0L);
        given(todaySummary.getTotalRecords()).willReturn(10L);

        given(attendanceRepository.getOverallAttendanceSummary(
                any(LocalDate.class), any(LocalDate.class), eq(true), eq(matchingDeptIds), eq(false), any(), any(), any(), any()
        )).willReturn(todaySummary);

        StudentAttendanceSummary lowStudent = mock(StudentAttendanceSummary.class);
        given(lowStudent.getTotalPeriods()).willReturn(100L);
        given(lowStudent.getPresentPeriods()).willReturn(60L); // 60% < 75%

        StudentAttendanceSummary goodStudent = mock(StudentAttendanceSummary.class);
        given(goodStudent.getTotalPeriods()).willReturn(100L);
        given(goodStudent.getPresentPeriods()).willReturn(90L); // 90% >= 75%

        given(attendanceRepository.getStudentAttendanceSummary(
                any(LocalDate.class), any(LocalDate.class), eq(true), eq(matchingDeptIds), eq(false), any(), any(), any(), any()
        )).willReturn(List.of(lowStudent, goodStudent));

        DashboardSummaryResponse res = dashboardService.getDashboardStats();

        assertThat(res).isNotNull();
        assertThat(res.getTotalStudents()).isEqualTo(50L);
        assertThat(res.getTotalStaff()).isEqualTo(4L);
        assertThat(res.getTodayAttendanceRate()).isEqualTo(100.0);
        assertThat(res.getTodayPresent()).isEqualTo(10L);
        assertThat(res.getTodayAbsent()).isEqualTo(0L);
        assertThat(res.getTodayTotal()).isEqualTo(10L);
        assertThat(res.getLowAttendanceCount()).isEqualTo(1L);

        // Fallback: only the staff's department must be shown in departments list
        assertThat(res.getDepartments()).hasSize(1);
        assertThat(res.getDepartments().get(0).getDepartment()).isEqualTo("Computer Applications");
        assertThat(res.getDepartments().get(0).getTotalStudents()).isEqualTo(50L);
        assertThat(res.getDepartments().get(0).getPresent()).isEqualTo(10L);
        assertThat(res.getDepartments().get(0).getAbsent()).isEqualTo(0L);
        assertThat(res.getDepartments().get(0).getAttendanceRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("STAFF login breaks down live attendance by course, year and section (BCA & MCA)")
    void staffDashboard_breaksDownByCourseYearSection() {
        String deptId = "26374668-1367-46cc-aae6-f53ecfd0bff3";
        setSecurityContext(Role.STAFF, deptId);

        List<String> matchingDeptIds = List.of(deptId, "CA", "Computer Applications");
        given(metadataCacheService.getMatchingDeptIdentifiers(deptId)).willReturn(matchingDeptIds);

        given(studentRepository.countByDepartmentIdInAndActiveTrue(matchingDeptIds)).willReturn(50L);
        given(userRepository.countByDepartmentIdInAndRoleInAndActiveTrue(eq(matchingDeptIds), any())).willReturn(4L);

        OverallAttendanceSummary todaySummary = mock(OverallAttendanceSummary.class);
        given(todaySummary.getPresentRecords()).willReturn(50L);
        given(todaySummary.getAbsentRecords()).willReturn(0L);
        given(todaySummary.getTotalRecords()).willReturn(50L);

        given(attendanceRepository.getOverallAttendanceSummary(
                any(LocalDate.class), any(LocalDate.class), eq(true), eq(matchingDeptIds), eq(false), any(), any(), any(), any()
        )).willReturn(todaySummary);

        given(attendanceRepository.getStudentAttendanceSummary(
                any(LocalDate.class), any(LocalDate.class), eq(true), eq(matchingDeptIds), eq(false), any(), any(), any(), any()
        )).willReturn(List.of());

        ClassAttendanceSummary bca1 = mock(ClassAttendanceSummary.class);
        given(bca1.getCourseId()).willReturn("course-bca");
        given(bca1.getYear()).willReturn(1);
        given(bca1.getSection()).willReturn("A");
        given(bca1.getTotalStudents()).willReturn(15L);
        given(bca1.getPresentCount()).willReturn(0L);
        given(bca1.getAbsentCount()).willReturn(0L);
        given(bca1.getTotalCount()).willReturn(0L);

        ClassAttendanceSummary mca1 = mock(ClassAttendanceSummary.class);
        given(mca1.getCourseId()).willReturn("course-mca");
        given(mca1.getYear()).willReturn(1);
        given(mca1.getSection()).willReturn("A");
        given(mca1.getTotalStudents()).willReturn(10L);
        given(mca1.getPresentCount()).willReturn(50L);
        given(mca1.getAbsentCount()).willReturn(0L);
        given(mca1.getTotalCount()).willReturn(50L);

        given(metadataCacheService.resolveCourseName("course-bca")).willReturn("BCA");
        given(metadataCacheService.resolveCourseName("course-mca")).willReturn("MCA");

        given(attendanceRepository.getClassAttendanceSummary(any(LocalDate.class), eq(matchingDeptIds)))
                .willReturn(List.of(bca1, mca1));

        DashboardSummaryResponse res = dashboardService.getDashboardStats();

        assertThat(res).isNotNull();
        assertThat(res.getTotalStudents()).isEqualTo(50L);
        assertThat(res.getDepartments()).hasSize(2);
        assertThat(res.getDepartments().get(0).getDepartment()).isEqualTo("BCA I - Sec A");
        assertThat(res.getDepartments().get(0).getTotalStudents()).isEqualTo(15L);
        assertThat(res.getDepartments().get(1).getDepartment()).isEqualTo("MCA I - Sec A");
        assertThat(res.getDepartments().get(1).getTotalStudents()).isEqualTo(10L);
        assertThat(res.getDepartments().get(1).getAttendanceRate()).isEqualTo(100.0);
    }

    @Test
    @DisplayName("ADMIN login provides college-wide statistics across all departments")
    void adminDashboard_providesCollegeWideStatistics() {
        setSecurityContext(Role.ADMIN, "ADMIN");

        given(studentRepository.countByActiveTrue()).willReturn(372L);
        given(userRepository.countByRoleInAndActiveTrue(any())).willReturn(62L);

        OverallAttendanceSummary todaySummary = mock(OverallAttendanceSummary.class);
        given(todaySummary.getPresentRecords()).willReturn(300L);
        given(todaySummary.getAbsentRecords()).willReturn(20L);
        given(todaySummary.getTotalRecords()).willReturn(320L);

        given(attendanceRepository.getOverallAttendanceSummary(
                any(LocalDate.class), any(LocalDate.class), eq(false), any(), eq(false), any(), any(), any(), any()
        )).willReturn(todaySummary);

        given(attendanceRepository.getStudentAttendanceSummary(
                any(LocalDate.class), any(LocalDate.class), eq(false), any(), eq(false), any(), any(), any(), any()
        )).willReturn(List.of());

        Department dept1 = new Department("Computer Applications", "CA", true);
        dept1.setId("dept-ca");
        Department dept2 = new Department("Mathematics", "MATH", true);
        dept2.setId("dept-math");

        given(departmentRepository.findByActiveTrue()).willReturn(List.of(dept1, dept2));

        DashboardSummaryResponse res = dashboardService.getDashboardStats();

        assertThat(res).isNotNull();
        assertThat(res.getTotalStudents()).isEqualTo(372L);
        assertThat(res.getTotalStaff()).isEqualTo(62L);
        assertThat(res.getDepartments()).hasSize(2);
    }
}
