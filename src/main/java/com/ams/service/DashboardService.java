package com.ams.service;

import java.time.LocalDate;
import java.util.List;

import org.springframework.stereotype.Service;

import com.ams.dto.DashboardSummaryResponse;
import com.ams.dto.DepartmentReportResponse;
import com.ams.dto.OverallReportResponse;
import com.ams.dto.StudentReportResponse;
import com.ams.model.Role;
import com.ams.repository.StudentRepository;
import com.ams.repository.UserRepository;

@Service
public class DashboardService {

    private final StudentRepository studentRepository;
    private final UserRepository userRepository;
    private final ReportService reportService;

    public DashboardService(
            StudentRepository studentRepository,
            UserRepository userRepository,
            ReportService reportService) {
        this.studentRepository = studentRepository;
        this.userRepository = userRepository;
        this.reportService = reportService;
    }

    public DashboardSummaryResponse getDashboardStats() {
        long totalStudents = studentRepository.countByActiveTrue();
        long totalStaff = userRepository.countByRoleInAndActiveTrue(List.of(Role.STAFF, Role.HOD, Role.VP, Role.ADMIN));

        LocalDate today = LocalDate.now();
        OverallReportResponse todayReport = reportService.getOverallReport(
                today, today, null, null, null, null, null
        );

        List<StudentReportResponse> lowAttendance = reportService.getLowAttendanceReports(
                today.minusMonths(1), today, null, null, null, null, null, 75.0
        );

        List<DepartmentReportResponse> deptReports = reportService.getDepartmentReports(
                today, today, null, null, null, null, null
        );

        long present = todayReport != null ? todayReport.getPresentRecords() : 0;
        long absent = todayReport != null ? todayReport.getAbsentRecords() : 0;
        long total = todayReport != null ? todayReport.getTotalRecords() : 0;
        double rate = todayReport != null ? todayReport.getOverallPercentage() : 0.0;
        long lowCount = lowAttendance != null ? lowAttendance.size() : 0;

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
