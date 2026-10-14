package com.ams.service;

import java.time.LocalDate;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.ams.dto.DashboardSummaryResponse;
import com.ams.dto.DepartmentAttendanceSummary;
import com.ams.dto.DepartmentReportResponse;
import com.ams.dto.OverallAttendanceSummary;
import com.ams.dto.StudentAttendanceSummary;
import com.ams.model.Department;
import com.ams.model.Role;
import com.ams.repository.AttendanceRepository;
import com.ams.repository.DepartmentRepository;
import com.ams.repository.StudentRepository;
import com.ams.repository.UserRepository;
import com.ams.security.AuthenticatedUser;
import com.ams.security.SecurityUtils;

@Service
public class DashboardService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final AttendanceRepository attendanceRepository;
    private final DepartmentRepository departmentRepository;
    private final MetadataCacheService metadataCacheService;

    public DashboardService(
            StudentRepository studentRepository,
            UserRepository userRepository,
            AttendanceRepository attendanceRepository,
            DepartmentRepository departmentRepository,
            MetadataCacheService metadataCacheService) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.attendanceRepository = attendanceRepository;
        this.departmentRepository = departmentRepository;
        this.metadataCacheService = metadataCacheService;
    }

    public DashboardSummaryResponse getDashboardStats() {
        AuthenticatedUser currentUser = SecurityUtils.getCurrentUser();
        LocalDate today = LocalDate.now();

        boolean isStaffOrHod = currentUser != null &&
                (currentUser.getRole() == Role.STAFF || currentUser.getRole() == Role.HOD);
        String userDept = currentUser != null ? currentUser.getDepartmentId() : null;

        if (isStaffOrHod && userDept != null && !userDept.isBlank()) {
            return getDepartmentScopedStats(userDept, today);
        } else {
            return getCollegeWideStats(today);
        }
    }

    private DashboardSummaryResponse getDepartmentScopedStats(String userDept, LocalDate today) {
        List<String> matchingDeptIds = metadataCacheService.getMatchingDeptIdentifiers(userDept);
        if (matchingDeptIds == null || matchingDeptIds.isEmpty()) {
            matchingDeptIds = List.of(userDept);
        }

        // 1. Total Students in that department
        long totalStudents = studentRepository.countByDepartmentIdInAndActiveTrue(matchingDeptIds);

        // 2. Total Staff in that department
        long totalStaff = userRepository.countByDepartmentIdInAndRoleInAndActiveTrue(
                matchingDeptIds,
                List.of(Role.STAFF, Role.HOD, Role.ADMIN, Role.VP)
        );

        // 3. Today's Attendance for that department
        OverallAttendanceSummary todaySummary = attendanceRepository.getOverallAttendanceSummary(
                today,
                today,
                true,
                matchingDeptIds,
                false,
                List.of(""),
                null,
                null,
                null
        );

        long present = todaySummary != null && todaySummary.getPresentRecords() != null ? todaySummary.getPresentRecords() : 0;
        long absent = todaySummary != null && todaySummary.getAbsentRecords() != null ? todaySummary.getAbsentRecords() : 0;
        long total = todaySummary != null && todaySummary.getTotalRecords() != null ? todaySummary.getTotalRecords() : 0;
        double rate = total > 0 ? Math.round((present * 100.0 / total) * 10.0) / 10.0 : 0.0;

        // 4. Low Attendance Count in that department (< 75%) over the past 30 days
        List<StudentAttendanceSummary> studentSummaries = attendanceRepository.getStudentAttendanceSummary(
                today.minusMonths(1),
                today,
                true,
                matchingDeptIds,
                false,
                List.of(""),
                null,
                null,
                null
        );
        long lowCount = studentSummaries != null
                ? studentSummaries.stream()
                        .filter(s -> s.getTotalPeriods() != null && s.getTotalPeriods() > 0)
                        .filter(s -> {
                            long p = s.getPresentPeriods() != null ? s.getPresentPeriods() : 0;
                            long tot = s.getTotalPeriods();
                            double r = Math.round((p * 100.0 / tot) * 10.0) / 10.0;
                            return r < 75.0;
                        })
                        .count()
                : 0;

        // 5. Course, Year & Section Breakdown for the Department Live Attendance Graph & Table (e.g. BCA I - Sec A, MCA I - Sec A)
        List<com.ams.dto.ClassAttendanceSummary> classSummaries = attendanceRepository.getClassAttendanceSummary(today, matchingDeptIds);
        List<DepartmentReportResponse> classReports = new java.util.ArrayList<>();

        if (classSummaries != null && !classSummaries.isEmpty()) {
            for (com.ams.dto.ClassAttendanceSummary c : classSummaries) {
                String cName = metadataCacheService.resolveCourseName(c.getCourseId());
                int yr = c.getYear() != null ? c.getYear() : 1;
                String sec = (c.getSection() != null && !c.getSection().isBlank()) ? c.getSection().trim() : "A";
                String romanYear = (yr == 1) ? "I" : (yr == 2) ? "II" : (yr == 3) ? "III" : (yr == 4) ? "IV" : String.valueOf(yr);
                String label = cName + " " + romanYear + " - Sec " + sec;

                long cTotalStudents = c.getTotalStudents() != null ? c.getTotalStudents() : 0;
                long cPresent = c.getPresentCount() != null ? c.getPresentCount() : 0;
                long cAbsent = c.getAbsentCount() != null ? c.getAbsentCount() : 0;
                long cTotal = c.getTotalCount() != null ? c.getTotalCount() : 0;
                double cRate = cTotal > 0 ? Math.round((cPresent * 100.0 / cTotal) * 10.0) / 10.0 : 0.0;

                String classId = c.getCourseId() + "_" + yr + "_" + sec;
                classReports.add(new DepartmentReportResponse(
                        classId,
                        label,
                        cTotalStudents,
                        cPresent,
                        cAbsent,
                        cTotal,
                        cRate
                ));
            }
            classReports.sort(Comparator.comparing(DepartmentReportResponse::getDepartment));
        }

        if (classReports.isEmpty()) {
            String canonicalDeptName = metadataCacheService.resolveDepartmentName(userDept);
            classReports.add(new DepartmentReportResponse(
                    userDept,
                    canonicalDeptName,
                    totalStudents,
                    present,
                    absent,
                    total,
                    rate
            ));
        }

        return new DashboardSummaryResponse(
                totalStudents,
                totalStaff,
                rate,
                present,
                absent,
                total,
                lowCount,
                classReports
        );
    }

    private DashboardSummaryResponse getCollegeWideStats(LocalDate today) {
        long totalStudents = studentRepository.countByActiveTrue();
        long totalStaff = userRepository.countByRoleInAndActiveTrue(List.of(Role.STAFF, Role.HOD, Role.VP, Role.ADMIN));

        OverallAttendanceSummary todaySummary = attendanceRepository.getOverallAttendanceSummary(
                today,
                today,
                false,
                List.of(""),
                false,
                List.of(""),
                null,
                null,
                null
        );

        long present = todaySummary != null && todaySummary.getPresentRecords() != null ? todaySummary.getPresentRecords() : 0;
        long absent = todaySummary != null && todaySummary.getAbsentRecords() != null ? todaySummary.getAbsentRecords() : 0;
        long total = todaySummary != null && todaySummary.getTotalRecords() != null ? todaySummary.getTotalRecords() : 0;
        double rate = total > 0 ? Math.round((present * 100.0 / total) * 10.0) / 10.0 : 0.0;

        List<StudentAttendanceSummary> studentSummaries = attendanceRepository.getStudentAttendanceSummary(
                today.minusMonths(1),
                today,
                false,
                List.of(""),
                false,
                List.of(""),
                null,
                null,
                null
        );
        long lowCount = studentSummaries != null
                ? studentSummaries.stream()
                        .filter(s -> s.getTotalPeriods() != null && s.getTotalPeriods() > 0)
                        .filter(s -> {
                            long p = s.getPresentPeriods() != null ? s.getPresentPeriods() : 0;
                            long tot = s.getTotalPeriods();
                            double r = Math.round((p * 100.0 / tot) * 10.0) / 10.0;
                            return r < 75.0;
                        })
                        .count()
                : 0;

        List<DepartmentAttendanceSummary> summaries = attendanceRepository.getDepartmentAttendanceSummary(
                today,
                today,
                false,
                List.of(""),
                false,
                List.of(""),
                null,
                null,
                null
        );

        Map<String, DepartmentAttendanceSummary> summaryByDept = (summaries != null)
                ? summaries.stream()
                        .filter(s -> s.getDepartmentId() != null)
                        .collect(Collectors.toMap(DepartmentAttendanceSummary::getDepartmentId, s -> s, (a, b) -> a))
                : Collections.emptyMap();

        List<Department> activeDepts = departmentRepository.findByActiveTrue();
        Map<String, Department> deptMap = activeDepts != null
                ? activeDepts.stream().collect(Collectors.toMap(Department::getId, d -> d, (a, b) -> a))
                : Collections.emptyMap();

        Set<String> allDeptIds = new LinkedHashSet<>(deptMap.keySet());
        allDeptIds.addAll(summaryByDept.keySet());

        List<DepartmentReportResponse> deptReports = allDeptIds.stream().map(deptId -> {
            DepartmentAttendanceSummary sum = summaryByDept.get(deptId);
            Department deptObj = deptMap.get(deptId);
            String deptName = deptObj != null ? deptObj.getName() : metadataCacheService.resolveDepartmentName(deptId);
            long deptTotalStudents = sum != null && sum.getTotalStudents() != null ? sum.getTotalStudents() : 0;
            long deptPresent = sum != null && sum.getPresentCount() != null ? sum.getPresentCount() : 0;
            long deptAbsent = sum != null && sum.getAbsentCount() != null ? sum.getAbsentCount() : 0;
            long deptTotal = sum != null && sum.getTotalCount() != null ? sum.getTotalCount() : 0;
            double deptRate = deptTotal > 0 ? Math.round((deptPresent * 100.0 / deptTotal) * 10.0) / 10.0 : 0.0;

            return new DepartmentReportResponse(
                    deptId,
                    deptName,
                    deptTotalStudents,
                    deptPresent,
                    deptAbsent,
                    deptTotal,
                    deptRate
            );
        }).sorted(Comparator.comparing(DepartmentReportResponse::getDepartment)).toList();

        return new DashboardSummaryResponse(
                totalStudents,
                totalStaff,
                rate,
                present,
                absent,
                total,
                lowCount,
                deptReports
        );
    }
}
