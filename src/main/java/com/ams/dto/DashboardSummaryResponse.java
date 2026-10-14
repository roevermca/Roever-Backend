package com.ams.dto;

import java.util.List;

public class DashboardSummaryResponse {

    private long totalStudents;
    private long totalStaff;
    private double todayAttendanceRate;
    private long todayPresent;
    private long todayAbsent;
    private long todayTotal;
    private long lowAttendanceCount;
    private List<DepartmentReportResponse> departments;

    public DashboardSummaryResponse() {
    }

    public DashboardSummaryResponse(
            long totalStudents,
            long totalStaff,
            double todayAttendanceRate,
            long todayPresent,
            long todayAbsent,
            long todayTotal,
            long lowAttendanceCount,
            List<DepartmentReportResponse> departments) {
        this.totalStudents = totalStudents;
        this.totalStaff = totalStaff;
        this.todayAttendanceRate = todayAttendanceRate;
        this.todayPresent = todayPresent;
        this.todayAbsent = todayAbsent;
        this.todayTotal = todayTotal;
        this.lowAttendanceCount = lowAttendanceCount;
        this.departments = departments;
    }

    public long getTotalStudents() {
        return totalStudents;
    }

    public void setTotalStudents(long totalStudents) {
        this.totalStudents = totalStudents;
    }

    public long getTotalStaff() {
        return totalStaff;
    }

    public void setTotalStaff(long totalStaff) {
        this.totalStaff = totalStaff;
    }

    public double getTodayAttendanceRate() {
        return todayAttendanceRate;
    }

    public void setTodayAttendanceRate(double todayAttendanceRate) {
        this.todayAttendanceRate = todayAttendanceRate;
    }

    public long getTodayPresent() {
        return todayPresent;
    }

    public void setTodayPresent(long todayPresent) {
        this.todayPresent = todayPresent;
    }

    public long getTodayAbsent() {
        return todayAbsent;
    }

    public void setTodayAbsent(long todayAbsent) {
        this.todayAbsent = todayAbsent;
    }

    public long getTodayTotal() {
        return todayTotal;
    }

    public void setTodayTotal(long todayTotal) {
        this.todayTotal = todayTotal;
    }

    public long getLowAttendanceCount() {
        return lowAttendanceCount;
    }

    public void setLowAttendanceCount(long lowAttendanceCount) {
        this.lowAttendanceCount = lowAttendanceCount;
    }

    public List<DepartmentReportResponse> getDepartments() {
        return departments;
    }

    public void setDepartments(List<DepartmentReportResponse> departments) {
        this.departments = departments;
    }
}
