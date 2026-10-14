package com.ams.dto;

public interface DepartmentAttendanceSummary {
    String getDepartmentId();
    Long getTotalStudents();
    Long getPresentCount();
    Long getAbsentCount();
    Long getTotalCount();
}
